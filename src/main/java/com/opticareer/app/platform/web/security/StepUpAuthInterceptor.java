package com.opticareer.app.platform.web.security;

import com.opticareer.app.modules.identity.IdentityApi;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Controller-level interceptor inspecting {@link RequireRecentAuth} annotations.
 * <p>
 * Enforces PSD2 RTS Step-Up re-authentication for sensitive banking and mutation endpoints.
 * Verifies that the authenticated subject has confirmed master credentials within the
 * permitted maximum age window. If expired, emits WWW-Authenticate: StepUp and halts with 403.
 */
@Component
public class StepUpAuthInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(StepUpAuthInterceptor.class);
  private final IdentityApi identityApi;

  public StepUpAuthInterceptor(IdentityApi identityApi) {
    this.identityApi = identityApi;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    RequireRecentAuth annotation = handlerMethod.getMethodAnnotation(RequireRecentAuth.class);
    if (annotation == null) {
      annotation = handlerMethod.getBeanType().getAnnotation(RequireRecentAuth.class);
    }
    if (annotation == null) {
      return true;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null && request.getUserPrincipal() instanceof Authentication p) {
      auth = p;
    }
    if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
    }

    UUID owner;
    try {
      owner = UUID.fromString(auth.getName());
    } catch (IllegalArgumentException e) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid authentication principal.");
    }

    Duration maxAge = Duration.ofMinutes(annotation.maxAgeMinutes());
    boolean fresh = identityApi.hasRecentAuth(owner, maxAge);
    if (!fresh) {
      log.warn(
          "Blocked request to {}: user {} requires step-up re-authentication (maxAge={}m)",
          request.getRequestURI(),
          owner,
          annotation.maxAgeMinutes());
      response.setHeader(
          "WWW-Authenticate", "StepUp realm=\"opticareer\", max_age=" + maxAge.toSeconds());
      throw new DomainException(
          ErrorCode.FORBIDDEN,
          "Step-up re-authentication required. Re-authenticate via /api/v1/auth/reauthenticate to perform this action.");
    }

    return true;
  }
}
