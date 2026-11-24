package com.opticareer.app.config;

import com.opticareer.app.shared.id.UuidV7;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * High-security edge filter executing at HIGHEST_PRECEDENCE.
 * Enforces W3C/UUIDv7 correlation trace ID injection into MDC, request attributes,
 * and response headers for immutable audit tracing across all threads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLogger extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String TRACE_ATTRIBUTE = "traceId";
    private static final Logger log = LoggerFactory.getLogger(HttpRequestLogger.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSXXX");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String timestamp = OffsetDateTime.now().format(FORMATTER);

        String traceId = resolveTraceId(request);
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        MDC.put(TRACE_ATTRIBUTE, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            String clientIp = getClientIp(request);
            String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";

            log.info("[HTTP] traceId={} | time={} | ip={} | method={} | path={}{} | status={} | duration={}ms",
                    traceId,
                    timestamp,
                    clientIp,
                    request.getMethod(),
                    request.getRequestURI(),
                    query,
                    response.getStatus(),
                    durationMs
            );
            MDC.remove(TRACE_ATTRIBUTE);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incomingTrace = request.getHeader(TRACE_HEADER);
        if (incomingTrace == null || incomingTrace.isBlank()) {
            incomingTrace = request.getHeader("X-Request-Id");
        }
        if (incomingTrace != null && incomingTrace.matches("^[a-zA-Z0-9._:-]{8,64}$")) {
            return incomingTrace;
        }
        return UuidV7.next().toString();
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
