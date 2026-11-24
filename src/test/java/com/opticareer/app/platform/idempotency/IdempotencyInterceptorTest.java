package com.opticareer.app.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class IdempotencyInterceptorTest {

  private IdempotencyInterceptor interceptor;

  static class TestController {
    @Idempotent(scope = "payments", required = true)
    public void executePayment() {}

    @Idempotent(scope = "reports", required = false)
    public void optionalPayment() {}

    public void regularEndpoint() {}
  }

  private HandlerMethod paymentHandler;
  private HandlerMethod optionalHandler;
  private HandlerMethod regularHandler;

  @BeforeEach
  void setUp() throws Exception {
    interceptor = new IdempotencyInterceptor();
    TestController controller = new TestController();
    Method payMethod = TestController.class.getMethod("executePayment");
    Method optMethod = TestController.class.getMethod("optionalPayment");
    Method regMethod = TestController.class.getMethod("regularEndpoint");

    paymentHandler = new HandlerMethod(controller, payMethod);
    optionalHandler = new HandlerMethod(controller, optMethod);
    regularHandler = new HandlerMethod(controller, regMethod);
  }

  @Test
  @DisplayName("Non-annotated endpoint bypasses idempotency verification")
  void regularEndpointPasses() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, regularHandler);
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Missing Idempotency-Key on required endpoint throws DomainException with VALIDATION_FAILED")
  void missingRequiredKeyThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, paymentHandler))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.VALIDATION_FAILED)
        .hasMessageContaining("Idempotency-Key");
  }

  @Test
  @DisplayName("Missing Idempotency-Key on optional endpoint passes through")
  void missingOptionalKeyPasses() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, optionalHandler);
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Malformed Idempotency-Key with invalid characters throws DomainException")
  void malformedKeyThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Idempotency-Key", "invalid key with spaces!@#$%");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, paymentHandler))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.VALIDATION_FAILED)
        .hasMessageContaining("must be 8 to 100 characters");
  }

  @Test
  @DisplayName("Valid Idempotency-Key passes, attaches request attributes, and sets response header")
  void validKeySucceeds() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Idempotency-Key", "idem_20261125_order9911");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, paymentHandler);
    assertThat(result).isTrue();
    assertThat(request.getAttribute("idempotencyKey")).isEqualTo("idem_20261125_order9911");
    assertThat(request.getAttribute("idempotencyScope")).isEqualTo("payments");
    assertThat(response.getHeader("Idempotency-Key")).isEqualTo("idem_20261125_order9911");
  }
}
