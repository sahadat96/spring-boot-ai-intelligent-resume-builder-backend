package com.opticareer.app.platform.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OptiCareer API")
                .version("1.0")
                .description("Production-grade backend for OptiCareer intelligent resume builder"))
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
                      path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/logout-all")
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
