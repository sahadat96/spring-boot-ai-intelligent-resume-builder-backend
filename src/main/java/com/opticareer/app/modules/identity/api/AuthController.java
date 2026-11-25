package com.opticareer.app.modules.identity.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opticareer.app.modules.identity.application.IdentityService;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.idempotency.Idempotent;
import com.opticareer.app.platform.web.security.RequireRecentAuth;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Endpoints for user authentication, OTP verification, password reset, and session management")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final IdentityService service;
  private final AppProperties properties;

  public AuthController(IdentityService service) {
    this(service, null);
  }

  @Autowired
  public AuthController(IdentityService service, AppProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  // --- Registration & Verification DTOs ---

  @Schema(name = "RegisterRequest", description = "User registration payload containing full name, email, and password")
  public record Register(
      @Schema(description = "User's full name", example = "Emran Hossain", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 2, maxLength = 150)
      @NotBlank @Size(min = 2, max = 150) String fullName,

      @Schema(description = "User email address", example = "candidate@example.com", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254)
      @NotBlank @Email @Size(max = 254) String email,

      @Schema(description = "Password meeting security criteria (min 12, max 72 characters)", example = "CorrectHorseBatteryStaple123!", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 72)
      @NotBlank @Size(min = 12, max = 72) String password) {}

  @Schema(name = "RegisterResponse", description = "Acknowledgment response returned when registration is accepted")
  public record RegisterResponse(
      @Schema(description = "Informational status message", example = "If registration can proceed, check your email.")
      String message) {}

  @Schema(name = "VerifyOtpRequest", description = "Payload to verify a 6-digit OTP code")
  public record VerifyOtpRequest(
      @Schema(description = "User email address", example = "candidate@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Email @Size(max = 254) String email,

      @Schema(description = "6-digit numeric OTP code received via email", example = "482910", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits") String otp) {}

  @Schema(name = "EmailRequest", description = "Payload specifying user email for code dispatch")
  public record EmailRequest(
      @Schema(description = "User email address", example = "candidate@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Email @Size(max = 254) String email) {}

  @Schema(name = "VerifyResetOtpResponse", description = "Short-lived single-use token returned after verifying the password reset OTP")
  public record VerifyResetOtpResponse(
      @Schema(description = "Single-use token to authorize password reset", example = "dGhpcy1pcy1hLXJlc2V0LXRva2Vu...")
      String resetToken,

      @Schema(description = "Lifetime of the reset token in seconds", example = "900")
      int expiresIn,

      @Schema(description = "Status message", example = "Password reset code verified. Use resetToken to set a new password.")
      String message) {}

  @Schema(name = "ResetPasswordRequest", description = "Payload to reset account password using the resetToken from verify-reset-otp (or legacy email + otp)")
  public record ResetPasswordRequest(
      @Schema(description = "Single-use resetToken returned by POST /api/v1/auth/verify-reset-otp", example = "dGhpcy1pcy1hLXJlc2V0LXRva2Vu...")
      String resetToken,

      @Schema(description = "New secure password meeting security policy", example = "NewStrongPassword456!", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 72)
      @NotBlank @Size(min = 12, max = 72) String password,

      @Schema(description = "Optional legacy field if resetting directly with 6-digit OTP without calling verify-reset-otp", hidden = true)
      @Email @Size(max = 254) String email,

      @Schema(description = "Optional legacy 6-digit OTP if not using resetToken", hidden = true)
      @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits") String otp) {}

  @Schema(name = "LoginRequest", description = "Credentials required to authenticate and obtain tokens")
  public record LoginRequest(
      @Schema(description = "Account email address", example = "candidate@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Email @Size(max = 254) String email,

      @Schema(description = "Account password", example = "CorrectHorseBatteryStaple123!", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Size(max = 72) String password,

      @Schema(description = "Optional client device name (e.g. 'Chrome on macOS', 'iPhone 15'). If omitted, backend detects it from User-Agent.", example = "Chrome on macOS")
      @Size(max = 150) String deviceLabel) {}

  @Schema(name = "RefreshTokenRequest", description = "Optional payload for refresh token if not using HttpOnly cookie")
  public record RefreshTokenRequest(
      @Schema(description = "Refresh token string", example = "dGhpcy1pcy1hLXJlZnJlc2gtdG9rZW4...")
      String refreshToken) {}

  @Schema(name = "MessageResponse", description = "Standard operational message response")
  public record MessageResponse(
      @Schema(description = "Status or informational message", example = "Operation completed successfully.")
      String message) {}

  @Schema(name = "AuthResponse", description = "Authentication tokens and metadata returned on successful login or refresh")
  public record Access(
      @Schema(description = "Signed JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
      String accessToken,

      @Schema(description = "Token type", example = "Bearer")
      String tokenType,

      @Schema(description = "Lifetime of the access token in seconds", example = "600")
      int expiresIn,

      @Schema(description = "Unique user ID", example = "018f3a8b-1234-7000-8000-000000000001")
      UUID userId) {}

  @Schema(name = "SessionInfo", description = "Details of an active login session or connected device")
  public record SessionInfo(
      @Schema(description = "Session family identifier", example = "018f3a8b-1234-7000-8000-000000000001")
      UUID id,
      @Schema(description = "Device or browser label", example = "Chrome on macOS")
      String deviceLabel,
      @Schema(description = "Timestamp when session was initiated")
      Instant createdAt,
      @Schema(description = "Timestamp when session expires")
      Instant expiresAt,
      @Schema(description = "Whether the session is currently active")
      boolean active,
      @Schema(description = "Whether this session represents the caller's current device")
      boolean current) {}

  @Schema(name = "ReauthenticateRequest", description = "Credentials required for high-risk step-up authentication")
  public record ReauthenticateRequest(
      @Schema(description = "Account master password", example = "CorrectHorseBatteryStaple123!", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Size(max = 72) String password) {}

  @Schema(name = "OAuthChallengeResponse", description = "Single-use anti-replay nonce challenge for OIDC sign-in")
  public record OAuthChallengeResponse(
      @Schema(description = "Cryptographic nonce to pass to Google Sign-In", example = "xK9vT2pQ...")
      String nonce,
      @Schema(description = "Challenge lifetime in seconds", example = "300")
      int expiresIn) {}

  @Schema(name = "GoogleAuthRequest", description = "Google OIDC ID Token payload for unified Google Sign-Up and Google Login")
  public record GoogleAuthRequest(
      @Schema(description = "Signed Google ID Token (JWT) obtained from Google Identity Services or mobile SDK", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank @Size(max = 4096) String idToken,

      @Schema(description = "Optional anti-replay nonce from GET /api/v1/auth/oauth/challenge", example = "xK9vT2pQ...")
      @Size(max = 256) String nonce,

      @Schema(description = "Optional client device label (auto-detected from User-Agent if omitted)", example = "Chrome on macOS")
      @Size(max = 150) String deviceLabel) {}

  // --- Endpoints ---

  @Operation(
      summary = "Register user account",
      description = "Registers a new user, creates their profile, generates a secure 6-digit verification OTP, and triggers email delivery.")
  @ApiResponses({
      @ApiResponse(responseCode = "202", description = "Registration accepted. Verification code dispatched via email."),
      @ApiResponse(responseCode = "400", description = "Validation error (e.g. invalid email format, weak password).")
  })
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public RegisterResponse register(@Valid @RequestBody Register r) {
    service.register(r.fullName(), r.email(), r.password());
    return new RegisterResponse("If registration can proceed, check your email.");
  }

  @Operation(
      summary = "Verify email with 6-digit OTP",
      description = "Verifies the user's email address using the 6-digit OTP code sent to their inbox.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Email verified successfully."),
      @ApiResponse(responseCode = "400", description = "Invalid or expired verification code.")
  })
  @PostMapping({"/verify-otp", "/verify-email"})
  public MessageResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest r) {
    service.verifyEmail(r.email(), r.otp());
    return new MessageResponse("Email verified successfully. You may now sign in.");
  }

  @Operation(
      summary = "Resend verification OTP",
      description = "Dispatches a fresh 6-digit verification OTP to the user's email if the account is unverified.")
  @ApiResponses({
      @ApiResponse(responseCode = "202", description = "Request accepted. A new code has been sent if eligible.")
  })
  @PostMapping({"/resend-otp", "/resend-verification"})
  @ResponseStatus(HttpStatus.ACCEPTED)
  public MessageResponse resendOtp(@Valid @RequestBody EmailRequest r) {
    service.resendVerification(r.email());
    return new MessageResponse("If verification can proceed, a new code has been sent to your email.");
  }

  @Operation(
      summary = "Request password reset OTP",
      description = "Dispatches a 6-digit password reset OTP to the user's email if the account exists.")
  @ApiResponses({
      @ApiResponse(responseCode = "202", description = "Request accepted. Password reset code sent if the account exists.")
  })
  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest r) {
    service.forgotPassword(r.email());
    return new MessageResponse("If the account exists, check your email for the password reset code.");
  }

  @Operation(
      summary = "Verify password reset OTP",
      description = "Consumes the 6-digit password reset OTP and returns a single-use resetToken valid for 15 minutes to submit on POST /api/v1/auth/reset-password.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Password reset OTP verified; resetToken issued."),
      @ApiResponse(responseCode = "401", description = "Invalid or expired password reset code."),
      @ApiResponse(responseCode = "429", description = "Too many verification attempts.")
  })
  @PostMapping("/verify-reset-otp")
  public VerifyResetOtpResponse verifyResetOtp(@Valid @RequestBody VerifyOtpRequest r) {
    String resetToken = service.verifyResetOtp(r.email(), r.otp());
    return new VerifyResetOtpResponse(
        resetToken, 900, "Password reset code verified. Use resetToken to set a new password.");
  }

  @Operation(
      summary = "Reset password",
      description = "Resets the account password using the resetToken from POST /api/v1/auth/verify-reset-otp (or email + otp) and invalidates all existing sessions.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Password reset successfully."),
      @ApiResponse(responseCode = "400", description = "Missing resetToken or weak password."),
      @ApiResponse(responseCode = "401", description = "Invalid or expired resetToken.")
  })
  @PostMapping("/reset-password")
  public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest r) {
    if (r.resetToken() != null && !r.resetToken().isBlank()) {
      service.resetPassword(null, r.resetToken(), r.password());
    } else if (r.email() != null && !r.email().isBlank() && r.otp() != null && !r.otp().isBlank()) {
      service.resetPassword(r.email(), r.otp(), r.password());
    } else {
      throw DomainException.invalid("Provide resetToken (or email and otp) to reset password.");
    }
    return new MessageResponse("Password reset successfully. Please sign in with your new password.");
  }

  @Operation(
      summary = "User login",
      description = "Authenticates user with email and password. Sets an HttpOnly refresh token cookie and returns a signed JWT access token.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Authentication successful."),
      @ApiResponse(responseCode = "401", description = "Invalid email or password."),
      @ApiResponse(responseCode = "403", description = "Email not verified.")
  })
  @PostMapping("/login")
  public ResponseEntity<Access> login(@Valid @RequestBody LoginRequest r, HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    String label = (r.deviceLabel() != null && !r.deviceLabel().isBlank())
        ? r.deviceLabel()
        : resolveDeviceLabel(userAgent);

    IdentityService.AuthResult result =
        service.login(
            r.email(),
            r.password(),
            label,
            request.getRemoteAddr(),
            userAgent);
    return authResponse(result);
  }

  @Operation(
      summary = "Issue OAuth anti-replay challenge",
      description = "Generates a single-use cryptographic nonce (valid 5 minutes) to bind to Google OIDC ID Tokens against replay attacks.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Challenge nonce issued.")
  })
  @GetMapping("/oauth/challenge")
  public ResponseEntity<OAuthChallengeResponse> oauthChallenge() {
    var challenge = service.issueOAuthChallenge();
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new OAuthChallengeResponse(challenge.nonce(), challenge.expiresIn()));
  }

  @Operation(
      summary = "Google OAuth Sign-In / Sign-Up",
      description = "Verifies a Google OIDC ID Token (RS256 JWKS, audience, issuer, email_verified, optional nonce). Automatically creates and provisions an account on first sign-in or links to an existing verified account, setting an HttpOnly refresh cookie and returning a JWT access token.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Google authentication successful."),
      @ApiResponse(responseCode = "401", description = "Invalid, expired, or untrusted Google ID token."),
      @ApiResponse(responseCode = "403", description = "Google email unverified or account deletion pending."),
      @ApiResponse(responseCode = "503", description = "Google Sign-In not configured.")
  })
  @PostMapping({"/oauth/google", "/google"})
  public ResponseEntity<Access> googleAuth(
      @Valid @RequestBody GoogleAuthRequest r, HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    String label = (r.deviceLabel() != null && !r.deviceLabel().isBlank())
        ? r.deviceLabel()
        : resolveDeviceLabel(userAgent);

    IdentityService.AuthResult result =
        service.googleAuth(
            r.idToken(),
            r.nonce(),
            label,
            request.getRemoteAddr(),
            userAgent);
    return authResponse(result);
  }

  @Operation(
      summary = "Refresh access token",
      description = "Rotates the refresh token and issues a new signed JWT access token. Enforces token family reuse detection.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Token refreshed successfully."),
      @ApiResponse(responseCode = "401", description = "Invalid, expired, or reused refresh token.")
  })
  @PostMapping("/refresh")
  public ResponseEntity<Access> refresh(
      @CookieValue(name = "refresh", required = false) String cookieToken,
      @RequestBody(required = false) RefreshTokenRequest body) {
    String token = (cookieToken != null && !cookieToken.isBlank())
        ? cookieToken
        : (body != null ? body.refreshToken() : null);
    if (token == null || token.isBlank()) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Refresh token is missing.");
    }
    return authResponse(service.refresh(token));
  }

  @Operation(
      summary = "Logout active session",
      description = "Revokes the current session family and clears the HttpOnly refresh token cookie.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Logged out successfully.")
  })
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "refresh", required = false) String cookieToken,
      @RequestBody(required = false) RefreshTokenRequest body) {
    String token = (cookieToken != null && !cookieToken.isBlank())
        ? cookieToken
        : (body != null ? body.refreshToken() : null);
    service.logout(token);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie()).build();
  }

  @Operation(
      summary = "Logout all devices",
      description = "Revokes all active sessions and refresh token families for the authenticated user.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "All sessions revoked."),
      @ApiResponse(responseCode = "401", description = "Authentication required.")
  })
  @PostMapping("/logout-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logoutAll(
      @Parameter(hidden = true) JwtAuthenticationToken token) {
    if (token == null) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
    }
    service.logoutAll(UUID.fromString(token.getName()));
  }

  @Operation(
      summary = "List active sessions",
      description = "Retrieves all active devices and sessions for the authenticated user, identifying which one is current.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Active sessions retrieved successfully."),
      @ApiResponse(responseCode = "401", description = "Authentication required.")
  })
  @GetMapping("/sessions")
  public ResponseEntity<List<SessionInfo>> sessions(
      @Parameter(hidden = true) JwtAuthenticationToken token) {
    if (token == null) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
    }
    UUID owner = UUID.fromString(token.getName());
    String currentSid = token.getToken() != null ? token.getToken().getClaimAsString("sid") : null;

    List<SessionInfo> list = service.sessions(owner).stream()
        .map(m -> new SessionInfo(
            (UUID) m.get("id"),
            (String) m.get("deviceLabel"),
            (Instant) m.get("createdAt"),
            (Instant) m.get("expiresAt"),
            !Boolean.TRUE.equals(m.get("revoked")),
            m.get("id") != null && m.get("id").toString().equals(currentSid)
        ))
        .toList();

    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(list);
  }

  @Operation(
      summary = "Revoke active session",
      description = "Terminates a specific device or session by its identifier. Requires step-up re-authentication within the last 15 minutes (PSD2 SCA).")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Session terminated successfully."),
      @ApiResponse(responseCode = "401", description = "Authentication required."),
      @ApiResponse(responseCode = "403", description = "Step-up re-authentication required (WWW-Authenticate: StepUp). Call POST /api/v1/auth/reauthenticate first.")
  })
  @DeleteMapping("/sessions/{id}")
  @RequireRecentAuth(maxAgeMinutes = 15)
  public ResponseEntity<Void> revokeSession(
      @PathVariable UUID id,
      @Parameter(hidden = true) JwtAuthenticationToken token) {
    if (token == null) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
    }
    UUID owner = UUID.fromString(token.getName());
    service.revoke(owner, id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Step-up reauthentication",
      description = "Confirms master password before high-risk transactional operations (PSD2 / fintech step-up requirement).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Step-up authentication verified successfully."),
      @ApiResponse(responseCode = "401", description = "Invalid password or authentication required.")
  })
  @PostMapping("/reauthenticate")
  @Idempotent(scope = "auth-reauth", required = false)
  public ResponseEntity<MessageResponse> reauthenticate(
      @Valid @RequestBody ReauthenticateRequest req,
      @Parameter(hidden = true) JwtAuthenticationToken token) {
    if (token == null) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
    }
    UUID owner = UUID.fromString(token.getName());
    service.reauthenticate(owner, req.password(), null);
    return ResponseEntity.ok(new MessageResponse("Step-up authentication verified."));
  }
  
  // --- Helper Methods ---

  private ResponseEntity<Access> authResponse(IdentityService.AuthResult r) {
    long maxAge = properties != null ? properties.security().refreshDays() * 86400L : 1209600L;
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie(r.refreshToken(), maxAge))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new Access(r.accessToken(), "Bearer", r.expiresIn(), r.userId()));
  }

  private String resolveDeviceLabel(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return "Web Browser";
    }
    String browser = "Browser";
    if (userAgent.contains("Edg/")) browser = "Edge";
    else if (userAgent.contains("Chrome/")) browser = "Chrome";
    else if (userAgent.contains("Firefox/")) browser = "Firefox";
    else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) browser = "Safari";

    String os = "Unknown OS";
    if (userAgent.contains("Windows")) os = "Windows";
    else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS")) os = "macOS";
    else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) os = "iOS";
    else if (userAgent.contains("Android")) os = "Android";
    else if (userAgent.contains("Linux")) os = "Linux";

    return browser + " on " + os;
  }

  private String cookie(String value, long age) {
    boolean secure = properties != null && properties.security().cookieSecure();
    return ResponseCookie.from("refresh", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(age)
        .build()
        .toString();
  }

  private String clearCookie() {
    boolean secure = properties != null && properties.security().cookieSecure();
    return ResponseCookie.from("refresh", "")
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(0)
        .build()
        .toString();
  }
}
