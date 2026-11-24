package com.opticareer.app.platform.idempotency;

import com.opticareer.app.shared.error.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Controller-level interceptor inspecting {@link Idempotent} annotations.
 * <p>
 * Validates the presence and syntactic safety of the {@code Idempotency-Key} HTTP header.
 * Ensures that financial mutations or resource modifications cannot be accidentally duplicated
 * through network retries or client-side double-submission.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
  public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
  public static final String ATTR_IDEMPOTENCY_KEY = "idempotencyKey";
  public static final String ATTR_IDEMPOTENCY_SCOPE = "idempotencyScope";
  private static final String KEY_PATTERN = "^[a-zA-Z0-9._:-]{8,100}$";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
    if (idempotent == null) {
      idempotent = handlerMethod.getBeanType().getAnnotation(Idempotent.class);
    }
    if (idempotent == null) {
      return true;
    }

    String key = request.getHeader(HEADER_IDEMPOTENCY_KEY);
    if (key == null || key.isBlank()) {
      if (idempotent.required()) {
        log.warn("Blocked request to {}: missing required Idempotency-Key", request.getRequestURI());
        throw DomainException.invalid(
            "An 'Idempotency-Key' header (8 to 100 safe characters) is required for this operation.");
      }
      return true;
    }

    if (!key.matches(KEY_PATTERN)) {
      log.warn("Blocked request to {}: malformed Idempotency-Key: {}", request.getRequestURI(), key);
      throw DomainException.invalid(
          "The provided 'Idempotency-Key' must be 8 to 100 characters containing only letters, numbers, '.', '_', ':', and '-'.");
    }

    request.setAttribute(ATTR_IDEMPOTENCY_KEY, key);
    request.setAttribute(ATTR_IDEMPOTENCY_SCOPE, idempotent.scope());
    response.setHeader(HEADER_IDEMPOTENCY_KEY, key);
    return true;
  }
}
