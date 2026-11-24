package com.opticareer.app.modules.identity.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.opticareer.app.modules.identity.application.IdentityService;
import com.opticareer.app.platform.web.ProblemHandler;
import com.opticareer.app.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

  private IdentityService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(IdentityService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(service))
            .setControllerAdvice(new ProblemHandler())
            .build();
  }

  @Test
  @DisplayName("POST /api/v1/auth/register returns 202 Accepted with confirmation message")
  void registerSuccess() throws Exception {
    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "applicant@example.com",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("If registration can proceed, check your email."));

    verify(service).register("Emran Hossain", "applicant@example.com", "CorrectHorseBatteryStaple123!");
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with blank full name returns 400 Bad Request")
  void registerBlankFullName() throws Exception {
    String payload = """
        {
          "fullName": "   ",
          "email": "applicant@example.com",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("fullName"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with single char full name returns 400 Bad Request")
  void registerShortFullName() throws Exception {
    String payload = """
        {
          "fullName": "A",
          "email": "applicant@example.com",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("fullName"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with invalid email returns 400 Bad Request with ProblemDetail")
  void registerInvalidEmail() throws Exception {
    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "not-an-email",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("email"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with short password returns 400 Bad Request")
  void registerShortPassword() throws Exception {
    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "applicant@example.com",
          "password": "Short1!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register propagates domain exception through ProblemHandler")
  void registerDomainException() throws Exception {
    doThrow(DomainException.invalid("Choose a less predictable password."))
        .when(service)
        .register("Emran Hossain", "applicant@example.com", "password12341234");

    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "applicant@example.com",
          "password": "password12341234"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.detail").value("Choose a less predictable password."));
  }

  @Test
  @DisplayName("POST /api/v1/auth/verify-otp successfully verifies 6-digit OTP code")
  void verifyOtpSuccess() throws Exception {
    String payload = """
        {
          "email": "applicant@example.com",
          "otp": "482910"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Email verified successfully. You may now sign in."));

    verify(service).verifyEmail("applicant@example.com", "482910");
  }

  @Test
  @DisplayName("POST /api/v1/auth/resend-otp triggers fresh OTP generation")
  void resendOtpSuccess() throws Exception {
    String payload = """
        {
          "email": "applicant@example.com"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("If verification can proceed, a new code has been sent to your email."));

    verify(service).resendVerification("applicant@example.com");
  }

  @Test
  @DisplayName("POST /api/v1/auth/forgot-password dispatches password reset OTP")
  void forgotPasswordSuccess() throws Exception {
    String payload = """
        {
          "email": "applicant@example.com"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("If the account exists, check your email for the password reset code."));

    verify(service).forgotPassword("applicant@example.com");
  }

  @Test
  @DisplayName("POST /api/v1/auth/reset-password resets password using OTP")
  void resetPasswordSuccess() throws Exception {
    String payload = """
        {
          "email": "applicant@example.com",
          "otp": "839201",
          "password": "NewStrongPassword456!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Password reset successfully. Please sign in with your new password."));

    verify(service).resetPassword("applicant@example.com", "839201", "NewStrongPassword456!");
  }

  @Test
  @DisplayName("POST /api/v1/auth/login returns JWT access token and sets refresh cookie")
  void loginSuccess() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();
    when(service.login(eq("applicant@example.com"), eq("CorrectPassword123!"), eq("Chrome on macOS"), any(), any()))
        .thenReturn(new IdentityService.AuthResult("access-jwt-token", "refresh-opaque-token", userId, 600));

    String payload = """
        {
          "email": "applicant@example.com",
          "password": "CorrectPassword123!",
          "deviceLabel": "Chrome on macOS"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-jwt-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(600))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.refreshToken").value("refresh-opaque-token"))
        .andExpect(header().exists("Set-Cookie"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/login auto-detects device label from User-Agent when omitted")
  void loginAutoDetectDeviceLabel() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();
    when(service.login(eq("applicant@example.com"), eq("CorrectPassword123!"), eq("Chrome on macOS"), any(), any()))
        .thenReturn(new IdentityService.AuthResult("access-jwt-token", "refresh-opaque-token", userId, 600));

    String payload = """
        {
          "email": "applicant@example.com",
          "password": "CorrectPassword123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-jwt-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-opaque-token"));

    verify(service).login(eq("applicant@example.com"), eq("CorrectPassword123!"), eq("Chrome on macOS"), any(), any());
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh rotates refresh token from cookie")
  void refreshSuccess() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();
    when(service.refresh("old-refresh-token"))
        .thenReturn(new IdentityService.AuthResult("new-access-jwt", "new-refresh-token", userId, 600));

    jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie("refresh", "old-refresh-token");

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(refreshCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access-jwt"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
        .andExpect(header().exists("Set-Cookie"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh rotates refresh token from JSON body")
  void refreshWithJsonBodySuccess() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();
    when(service.refresh("body-refresh-token"))
        .thenReturn(new IdentityService.AuthResult("new-access-jwt", "new-refresh-token", userId, 600));

    String payload = """
        {
          "refreshToken": "body-refresh-token"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access-jwt"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

    verify(service).refresh("body-refresh-token");
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout clears refresh cookie")
  void logoutSuccess() throws Exception {
    jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie("refresh", "active-token");

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(refreshCookie))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"));

    verify(service).logout("active-token");
  }

  @Test
  @DisplayName("GET /api/v1/auth/sessions returns active sessions list with current session indicator")
  void sessionsSuccess() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();
    java.util.UUID currentFamilyId = java.util.UUID.randomUUID();
    java.util.UUID otherFamilyId = java.util.UUID.randomUUID();
    java.time.Instant now = java.time.Instant.now();

    java.util.Map<String, Object> session1 = new java.util.LinkedHashMap<>();
    session1.put("id", currentFamilyId);
    session1.put("deviceLabel", "Chrome on macOS");
    session1.put("createdAt", now);
    session1.put("expiresAt", now.plusSeconds(86400));
    session1.put("revoked", false);

    java.util.Map<String, Object> session2 = new java.util.LinkedHashMap<>();
    session2.put("id", otherFamilyId);
    session2.put("deviceLabel", "Safari on iPhone");
    session2.put("createdAt", now.minusSeconds(3600));
    session2.put("expiresAt", now.plusSeconds(82800));
    session2.put("revoked", false);

    when(service.sessions(userId)).thenReturn(java.util.List.of(session1, session2));

    Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
        .header("alg", "none")
        .subject(userId.toString())
        .claim("sid", currentFamilyId.toString())
        .build();
    JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);

    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].deviceLabel").value("Chrome on macOS"))
        .andExpect(jsonPath("$[0].current").value(true))
        .andExpect(jsonPath("$[0].active").value(true))
        .andExpect(jsonPath("$[1].deviceLabel").value("Safari on iPhone"))
        .andExpect(jsonPath("$[1].current").value(false))
        .andExpect(jsonPath("$[1].active").value(true));

    verify(service).sessions(userId);
  }

  @Test
  @DisplayName("GET /api/v1/auth/sessions without auth token returns 401 Unauthenticated")
  void sessionsUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/sessions"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("DELETE /api/v1/auth/sessions/{id} revokes target session")
  void revokeSessionSuccess() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();
    java.util.UUID targetFamilyId = java.util.UUID.randomUUID();

    Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
        .header("alg", "none")
        .subject(userId.toString())
        .build();
    JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);

    mockMvc
        .perform(
            delete("/api/v1/auth/sessions/" + targetFamilyId)
                .principal(principal))
        .andExpect(status().isNoContent());

    verify(service).revoke(userId, targetFamilyId);
  }

  @Test
  @DisplayName("POST /api/v1/auth/reauthenticate performs step-up password verification")
  void reauthenticateSuccess() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();

    Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
        .header("alg", "none")
        .subject(userId.toString())
        .build();
    JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);

    String payload = """
        {
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/reauthenticate")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Step-up authentication verified."));

    verify(service).reauthenticate(userId, "CorrectHorseBatteryStaple123!", null);
  }

  @Test
  @DisplayName("POST /api/v1/auth/reauthenticate with wrong password returns 401")
  void reauthenticateInvalidPassword() throws Exception {
    java.util.UUID userId = java.util.UUID.randomUUID();

    Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
        .header("alg", "none")
        .subject(userId.toString())
        .build();
    JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);

    doThrow(new DomainException(com.opticareer.app.shared.error.ErrorCode.UNAUTHENTICATED, "Invalid email or password."))
        .when(service).reauthenticate(userId, "WrongPassword!", null);

    String payload = """
        {
          "password": "WrongPassword!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/reauthenticate")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }
}
