package com.opticareer.app.platform.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.opticareer.app.modules.identity.IdentityApi;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;

class StepUpAuthInterceptorTest {

  private IdentityApi identityApi;
  private StepUpAuthInterceptor interceptor;

  static class SecureBankingController {
    @RequireRecentAuth(maxAgeMinutes = 10)
    public void sensitiveWireTransfer() {}

    public void viewBalance() {}
  }

  private HandlerMethod sensitiveHandler;
  private HandlerMethod regularHandler;

  @BeforeEach
  void setUp() throws Exception {
    identityApi = mock(IdentityApi.class);
    interceptor = new StepUpAuthInterceptor(identityApi);
    SecureBankingController controller = new SecureBankingController();
    Method sensitiveMethod = SecureBankingController.class.getMethod("sensitiveWireTransfer");
    Method regularMethod = SecureBankingController.class.getMethod("viewBalance");

    sensitiveHandler = new HandlerMethod(controller, sensitiveMethod);
    regularHandler = new HandlerMethod(controller, regularMethod);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Non-annotated endpoint bypasses step-up auth check")
  void nonAnnotatedEndpointPasses() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, regularHandler);
    assertThat(result).isTrue();
    verifyNoInteractions(identityApi);
  }

  @Test
  @DisplayName("Unauthenticated request to @RequireRecentAuth endpoint throws UNAUTHENTICATED")
  void unauthenticatedRequestThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, sensitiveHandler))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.UNAUTHENTICATED);
  }

  @Test
  @DisplayName("Stale authentication emits WWW-Authenticate: StepUp header and throws FORBIDDEN")
  void staleAuthRejectsWithStepUpChallenge() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = Jwt.withTokenValue("mock-jwt").header("alg", "none").subject(userId.toString()).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

    when(identityApi.hasRecentAuth(userId, Duration.ofMinutes(10))).thenReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/banking/wire");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, sensitiveHandler))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.FORBIDDEN)
        .hasMessageContaining("Step-up re-authentication required");

    assertThat(response.getHeader("WWW-Authenticate")).contains("StepUp realm=\"opticareer\"");
  }

  @Test
  @DisplayName("Fresh recent authentication allows request through")
  void freshAuthPasses() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = Jwt.withTokenValue("mock-jwt").header("alg", "none").subject(userId.toString()).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

    when(identityApi.hasRecentAuth(userId, Duration.ofMinutes(10))).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/banking/wire");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, sensitiveHandler);
    assertThat(result).isTrue();
  }
}
