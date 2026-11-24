package com.opticareer.app.platform.web.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Global HTTP Filter enforcing API throttling across all REST endpoints.
 * <p>
 * Emits standard IETF rate-limiting headers:
 * <ul>
 *   <li>{@code X-RateLimit-Limit}: Maximum requests permitted in window</li>
 *   <li>{@code X-RateLimit-Remaining}: Remaining quota in window</li>
 *   <li>{@code X-RateLimit-Reset}: Seconds until quota resets</li>
 *   <li>{@code Retry-After}: Seconds caller must wait when throttled (HTTP 429)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiThrottleFilter extends OncePerRequestFilter {

  public static final long ANONYMOUS_LIMIT = 60L; // 60 requests per minute per IP
  public static final long AUTHENTICATED_LIMIT = 300L; // 300 requests per minute per authenticated user
  public static final long WINDOW_SECONDS = 60L;

  private final ApiRateLimiter rateLimiter;

  public ApiThrottleFilter(ApiRateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // Exclude documentation, swagger, and non-api health endpoints
    return !path.startsWith("/api/")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.equals("/hello");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    boolean isAuthenticated = authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 20;

    String throttleKey;
    long limit;

    if (isAuthenticated) {
      // Key by token fingerprint
      String tokenSnippet = authHeader.substring(7, Math.min(authHeader.length(), 40));
      throttleKey = "user:" + Integer.toHexString(tokenSnippet.hashCode());
      limit = AUTHENTICATED_LIMIT;
    } else {
      // Key by client IP address
      String ip = getClientIp(request);
      throttleKey = "ip:" + ip;
      limit = ANONYMOUS_LIMIT;
    }

    RateLimitResult result = rateLimiter.check(throttleKey, limit, WINDOW_SECONDS);

    // Standard IETF RateLimit Headers on all API responses
    response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
    response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));

    if (!result.allowed()) {
      response.setStatus(429); // 429 Too Many Requests
      response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.resetSeconds()));
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());

      String problemJson = String.format(
          """
          {
            "type": "urn:opticareer:problem:rate-limited",
            "title": "Too Many Requests",
            "status": 429,
            "detail": "API rate limit exceeded. Please retry after %d seconds.",
            "instance": "%s",
            "code": "RATE_LIMITED",
            "traceId": null,
            "errors": []
          }
          """,
          result.resetSeconds(),
          request.getRequestURI());

      response.getWriter().write(problemJson);
      response.getWriter().flush();
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
      return xRealIp.trim();
    }
    return request.getRemoteAddr();
  }
}
