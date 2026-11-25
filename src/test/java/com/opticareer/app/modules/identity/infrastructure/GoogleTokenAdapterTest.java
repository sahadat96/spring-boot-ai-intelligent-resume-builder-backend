package com.opticareer.app.modules.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opticareer.app.modules.identity.application.IdentityPorts;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class GoogleTokenAdapterTest {

  private static final String CLIENT_ID = "123456-test.apps.googleusercontent.com";

  private AppProperties props(String googleClientId) {
    AppProperties props = mock(AppProperties.class);
    AppProperties.Security sec = mock(AppProperties.Security.class);
    when(props.security()).thenReturn(sec);
    when(sec.googleClientId()).thenReturn(googleClientId);
    return props;
  }

  private Jwt googleJwt(String issuer, List<String> aud, boolean emailVerified, String nonce) {
    return new Jwt(
        "raw-token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "RS256"),
        Map.of(
            "iss", issuer,
            "aud", aud,
            "sub", "google-sub-12345",
            "email", "sahadat@gmail.com",
            "email_verified", emailVerified,
            "name", "Sahadat Hossain",
            "nonce", nonce));
  }

  @Test
  @DisplayName("Valid Google ID token passes all OIDC checks and extracts profile claims")
  void validTokenSucceeds() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("valid-jwt"))
        .thenReturn(googleJwt("https://accounts.google.com", List.of(CLIENT_ID), true, "nonce-abc"));

    GoogleTokenAdapter adapter = new GoogleTokenAdapter(props(CLIENT_ID), decoder);
    IdentityPorts.Google.Verified verified = adapter.verify("valid-jwt", "nonce-abc");

    assertThat(verified.subject()).isEqualTo("google-sub-12345");
    assertThat(verified.email()).isEqualTo("sahadat@gmail.com");
    assertThat(verified.fullName()).isEqualTo("Sahadat Hossain");
  }

  @Test
  @DisplayName("Missing GOOGLE_CLIENT_ID fails closed with PROVIDER_UNAVAILABLE")
  void missingClientIdThrows() {
    GoogleTokenAdapter adapter = new GoogleTokenAdapter(props(""), mock(JwtDecoder.class));

    assertThatThrownBy(() -> adapter.verify("any-token", null))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.PROVIDER_UNAVAILABLE);
  }

  @Test
  @DisplayName("Untrusted issuer is rejected with INVALID_TOKEN")
  void untrustedIssuerThrows() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("bad-iss"))
        .thenReturn(googleJwt("https://evil.example.com", List.of(CLIENT_ID), true, "n"));

    GoogleTokenAdapter adapter = new GoogleTokenAdapter(props(CLIENT_ID), decoder);

    assertThatThrownBy(() -> adapter.verify("bad-iss", null))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("Audience mismatch is rejected with INVALID_TOKEN")
  void audienceMismatchThrows() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("bad-aud"))
        .thenReturn(googleJwt("https://accounts.google.com", List.of("other-app-id"), true, "n"));

    GoogleTokenAdapter adapter = new GoogleTokenAdapter(props(CLIENT_ID), decoder);

    assertThatThrownBy(() -> adapter.verify("bad-aud", null))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("Unverified Google email is rejected with EMAIL_NOT_VERIFIED")
  void unverifiedEmailThrows() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("unverified"))
        .thenReturn(googleJwt("https://accounts.google.com", List.of(CLIENT_ID), false, "n"));

    GoogleTokenAdapter adapter = new GoogleTokenAdapter(props(CLIENT_ID), decoder);

    assertThatThrownBy(() -> adapter.verify("unverified", null))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.EMAIL_NOT_VERIFIED);
  }

  @Test
  @DisplayName("Nonce mismatch is rejected with INVALID_TOKEN")
  void nonceMismatchThrows() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("bad-nonce"))
        .thenReturn(googleJwt("https://accounts.google.com", List.of(CLIENT_ID), true, "expected-nonce"));

    GoogleTokenAdapter adapter = new GoogleTokenAdapter(props(CLIENT_ID), decoder);

    assertThatThrownBy(() -> adapter.verify("bad-nonce", "wrong-nonce"))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.INVALID_TOKEN);
  }
}
