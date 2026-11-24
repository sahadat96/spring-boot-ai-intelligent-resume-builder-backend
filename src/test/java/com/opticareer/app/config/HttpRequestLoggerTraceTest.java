package com.opticareer.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpRequestLoggerTraceTest {

  private HttpRequestLogger filter;

  @BeforeEach
  void setUp() {
    filter = new HttpRequestLogger();
    MDC.clear();
  }

  @Test
  @DisplayName("Generates UUIDv7 trace ID when no incoming trace header is present")
  void generatesTraceIdWhenAbsent() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/sessions");
    MockHttpServletResponse response = new MockHttpServletResponse();

    final String[] capturedMdcTrace = new String[1];
    FilterChain chain =
        (req, res) -> {
          capturedMdcTrace[0] = MDC.get("traceId");
          assertThat(req.getAttribute("traceId")).isNotNull();
        };

    filter.doFilterInternal(request, response, chain);

    String traceHeader = response.getHeader("X-Trace-Id");
    assertThat(traceHeader).isNotBlank();
    assertThat(capturedMdcTrace[0]).isEqualTo(traceHeader);
    // Verifies MDC is cleared after execution to prevent thread-pool memory leaks
    assertThat(MDC.get("traceId")).isNull();
  }

  @Test
  @DisplayName("Propagates valid incoming X-Request-Id as the trace ID")
  void propagatesIncomingTraceId() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.addHeader("X-Request-Id", "req_9988776655aabbcc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Trace-Id")).isEqualTo("req_9988776655aabbcc");
    assertThat(request.getAttribute("traceId")).isEqualTo("req_9988776655aabbcc");
  }

  @Test
  @DisplayName("Replaces malformed or injection-prone trace ID with a secure UUIDv7")
  void sanitizesMalformedTraceId() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/sessions");
    request.addHeader("X-Trace-Id", "<script>alert(1)</script> dangerous header");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (req, res) -> {});

    String sanitizedTrace = response.getHeader("X-Trace-Id");
    assertThat(sanitizedTrace).doesNotContain("<script>");
    assertThat(sanitizedTrace).matches("^[0-9a-fA-F-]{36}$");
  }
}
