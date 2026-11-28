package com.opticareer.app.platform.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  private static final java.util.Set<String> AUTHENTICATED_AUTH_PATHS =
      java.util.Set.of(
          "/api/v1/auth/logout-all",
          "/api/v1/auth/sessions",
          "/api/v1/auth/sessions/{id}",
          "/api/v1/auth/reauthenticate");

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OptiCareer API")
                .version("1.0")
                .description(
                    """
                    Production-grade backend for OptiCareer intelligent resume builder.

                    ### Authentication & Security Contract
                    - **Access Token:** Short-lived JWT (`600s`) sent via `Authorization: Bearer <token>`.
                    - **Refresh Token:** Delivered exclusively via `HttpOnly; SameSite=Lax` cookie (`refresh`) on `/api/v1/auth/login` and rotated on `/api/v1/auth/refresh`. Web clients must use `credentials: 'include'`.
                    - **Step-Up Auth (PSD2 SCA):** Sensitive endpoints (e.g., `DELETE /api/v1/auth/sessions/{id}`) require recent password verification via `POST /api/v1/auth/reauthenticate`. Stale sessions receive `403 Forbidden` with `WWW-Authenticate: StepUp`.
                    - **Rate Limiting & Tracing:** All `/api/**` responses emit `X-Trace-Id` and `X-RateLimit-*` headers (`60 req/min` anonymous IP, `300 req/min` authenticated). Errors follow RFC 7807 `application/problem+json`.

                    ### Resume Analysis & Async Queue Contract
                    - **Upload & Analyze Flow:** `POST /api/v1/resume-analysis/upload` -> `POST /api/v1/resume-analysis/{fileId}/analyze` (`202 Accepted` queued in Redis) -> poll `GET /api/v1/resume-analysis/{id}` until `status` is `COMPLETED` or `FAILED`.
                    - **Free vs. Premium AI Layer:** All parsing (`PDFBox -> Tika -> Tesseract`) and ATS/Content/Formatting scoring are deterministic and free. Subscribed/premium users automatically receive post-analysis AI enrichment (`aiEnhanced: true`, `aiSuggestions`) without altering algorithmic scores.

                    ### Workspace Dashboard Contract
                    - **Aggregated Snapshot:** `GET /api/v1/dashboard` synthesizes the user's profile, billing plan, latest completed resume analysis, top best-fit roles, biggest match-lift opportunity, profile highlights, and recommended next moves in a single call.
                    """))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  public org.springdoc.core.customizers.OpenApiCustomizer publicOperations() {
    return api ->
        api.getPaths()
            .forEach(
                (path, item) -> {
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) ->
                              operation.setOperationId(
                                  method.name().toLowerCase(java.util.Locale.ROOT)
                                      + "_"
                                      + path.replaceAll("[^A-Za-z0-9]+", "_")
                                          .replaceAll("^_|_$", "")));
                  boolean open =
                      (path.startsWith("/api/v1/auth/") && !AUTHENTICATED_AUTH_PATHS.contains(path))
                          || java.util.Set.of(
                                  "/api/v1/billing/plans",
                                  "/api/v1/resume-templates",
                                  "/api/v1/cover-letter-templates",
                                  "/api/v1/webhooks/stripe")
                              .contains(path);
                  if (open)
                    item.readOperations()
                        .forEach(operation -> operation.setSecurity(java.util.List.of()));
                });
  }
}
