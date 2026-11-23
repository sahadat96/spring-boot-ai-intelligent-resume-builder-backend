package com.opticareer.app.platform.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.opticareer.app.modules.identity.IdentityApi;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecretKeySpec jwtSecret(AppProperties p) {
    byte[] bytes = Base64.getDecoder().decode(p.security().jwtKey());
    if (bytes.length < 32) {
      throw new IllegalArgumentException("JWT_KEY must encode at least 32 bytes.");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key, AppProperties properties, IdentityApi identity) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> sessionValidator =
        jwt -> {
          try {
            if (!jwt.getAudience().contains("opticareer-api")) {
              throw new IllegalArgumentException();
            }
            identity.requireSession(
                UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
            return OAuth2TokenValidatorResult.success();
          } catch (RuntimeException e) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Session unavailable.", null));
          }
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.security().issuer()), sessionValidator));
    return decoder;
  }

  @Bean
  CorsConfigurationSource cors(AppProperties p) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.stream(p.corsOrigins().split(",")).map(String::trim).toList());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "If-Match",
            "Idempotency-Key",
            "X-CSRF-TOKEN",
            "X-XSRF-TOKEN"));
    c.setExposedHeaders(List.of("ETag", "X-Request-Id", "Retry-After", "Location"));
    c.setAllowCredentials(true);
    c.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, AppProperties p, JwtDecoder jwtDecoder) throws Exception {
    return http
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/hello",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder)))
        .headers(
            h ->
                h.frameOptions(f -> f.disable())
                    .addHeaderWriter(
                        (request, response) -> {
                          if (request.getRequestURI().startsWith("/swagger-ui")) {
                            response.setHeader(
                                "Content-Security-Policy",
                                "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'");
                          } else {
                            response.setHeader("X-Frame-Options", "DENY");
                            response.setHeader(
                                "Content-Security-Policy",
                                "default-src 'none'; frame-ancestors 'none'");
                          }
                          response.setHeader("Cache-Control", "no-store");
                        }))
        .build();
  }
}
