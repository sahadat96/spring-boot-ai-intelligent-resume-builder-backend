package com.opticareer.app.platform.config;

import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

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
  SecurityFilterChain security(HttpSecurity http, AppProperties p) throws Exception {
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
