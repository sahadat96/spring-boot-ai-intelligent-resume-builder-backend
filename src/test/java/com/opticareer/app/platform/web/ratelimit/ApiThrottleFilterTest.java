package com.opticareer.app.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiThrottleFilterTest {

  private ApiRateLimiter rateLimiter;
  private ApiThrottleFilter filter;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    rateLimiter = mock(ApiRateLimiter.class);
    filter = new ApiThrottleFilter(rateLimiter);
    chain = mock(FilterChain.class);
  }

  @Test
  @DisplayName("Anonymous request within limit passes through and sets standard rate limit headers")
  void anonymousRequestWithinLimitPasses() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resumes");
    request.setRemoteAddr("192.168.1.50");
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(rateLimiter.check(eq("ip:192.168.1.50"), eq(60L), eq(60L)))
        .thenReturn(new RateLimitResult(true, 60, 45, 30));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
    assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("45");
    assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("30");
  }

  @Test
  @DisplayName("Authenticated request receives elevated 300 quota keyed by user token")
  void authenticatedRequestGetsHigherQuota() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/sessions");
    request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMifQ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(rateLimiter.check(startsWith("user:"), eq(300L), eq(60L)))
        .thenReturn(new RateLimitResult(true, 300, 280, 50));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("300");
    assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("280");
  }

  @Test
  @DisplayName("Request exceeding rate limit returns 429 Too Many Requests, Retry-After, and ProblemDetail")
  void requestExceedingLimitReturns429() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr("203.0.113.195");
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(rateLimiter.check(eq("ip:203.0.113.195"), eq(60L), eq(60L)))
        .thenReturn(new RateLimitResult(false, 60, 0, 25)); // 25s remaining to reset

    filter.doFilter(request, response, chain);

    verifyNoInteractions(chain); // Request must NOT proceed to controller!
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("25");
    assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    assertThat(response.getContentType()).contains("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("RATE_LIMITED")
        .contains("API rate limit exceeded. Please retry after 25 seconds.")
        .contains("/api/v1/auth/login");
  }

  @Test
  @DisplayName("Non-API requests bypass rate limiting filter entirely")
  void nonApiEndpointsBypassRateLimiter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(rateLimiter);
  }
}
