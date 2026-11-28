package com.opticareer.app.modules.identity.infrastructure;

import com.opticareer.app.modules.identity.application.IdentityPorts;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Cryptographic Google OIDC ID Token verifier backed by Google's public RS256 JWKS keys.
 * <p>
 * Enforces strict issuer, audience ({@code GOOGLE_CLIENT_ID}), expiration,
 * {@code email_verified=true}, and optional {@code nonce} anti-replay validation.
 */
@Component
public class GoogleTokenAdapter implements IdentityPorts.Google {

  private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
  private static final List<String> VALID_ISSUERS =
      List.of("https://accounts.google.com", "accounts.google.com");

  private final AppProperties properties;
  private final JwtDecoder customDecoder;
  private volatile JwtDecoder jwksDecoder;

  @Autowired
  public GoogleTokenAdapter(AppProperties properties) {
    this(properties, null);
  }

  public GoogleTokenAdapter(AppProperties properties, JwtDecoder customDecoder) {
    this.properties = properties;
    this.customDecoder = customDecoder;
  }

  @Override
  public Verified verify(String token, String nonce) {
    String clientId =
        properties != null && properties.security() != null
            ? properties.security().googleClientId()
            : null;
    if (clientId == null || clientId.isBlank()) {
      throw new DomainException(
          ErrorCode.PROVIDER_UNAVAILABLE, "Google Sign-In is not configured on this server.");
    }
    if (token == null || token.isBlank()) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Google ID token is required.");
    }

    Jwt jwt;
    try {
      jwt = decoder().decode(token);
    } catch (JwtException e) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Invalid or expired Google ID token.");
    }

    String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
    if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Untrusted Google token issuer.");
    }

    List<String> audience = jwt.getAudience();
    if (audience == null || !audience.contains(clientId)) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Google token audience mismatch.");
    }

    Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
    if (!Boolean.TRUE.equals(emailVerified)) {
      throw new DomainException(
          ErrorCode.EMAIL_NOT_VERIFIED, "Google account email must be verified.");
    }

    if (nonce != null && !nonce.isBlank()) {
      String tokenNonce = jwt.getClaimAsString("nonce");
      if (!Objects.equals(nonce, tokenNonce)) {
        throw new DomainException(ErrorCode.INVALID_TOKEN, "Google token nonce mismatch.");
      }
    }

    String subject = jwt.getSubject();
    String email = jwt.getClaimAsString("email");
    if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Google token is missing subject or email.");
    }

    String firstName = normalizeClaim(jwt.getClaimAsString("given_name"));
    String lastName = normalizeClaim(jwt.getClaimAsString("family_name"));
    String name = normalizeClaim(jwt.getClaimAsString("name"));
    if (name == null) {
      if (firstName != null && lastName != null) {
        name = firstName + " " + lastName;
      } else if (firstName != null) {
        name = firstName;
      } else {
        name = lastName;
      }
    }
    return new Verified(subject, email, name, firstName, lastName);
  }

  private static String normalizeClaim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private JwtDecoder decoder() {
    if (customDecoder != null) {
      return customDecoder;
    }
    JwtDecoder local = jwksDecoder;
    if (local == null) {
      synchronized (this) {
        local = jwksDecoder;
        if (local == null) {
          local = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
          jwksDecoder = local;
        }
      }
    }
    return local;
  }
}
