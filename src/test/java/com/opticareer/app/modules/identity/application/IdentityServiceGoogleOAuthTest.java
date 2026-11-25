package com.opticareer.app.modules.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.identity.domain.IdentityRepository;
import com.opticareer.app.modules.identity.domain.User;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.jobs.JobQueue;
import com.opticareer.app.shared.event.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class IdentityServiceGoogleOAuthTest {

  private IdentityRepository repository;
  private IdentityPorts.Google google;
  private AccountApi accounts;
  private EventPublisher events;
  private IdentityService service;
  private final Instant now = Instant.parse("2026-11-25T07:00:00Z");

  @BeforeEach
  void setUp() {
    repository = mock(IdentityRepository.class);
    IdentityPorts.LoginLimits loginLimits = mock(IdentityPorts.LoginLimits.class);
    IdentityPorts.Passwords passwords = mock(IdentityPorts.Passwords.class);
    when(passwords.hash(anyString())).thenReturn("dummy-hash");

    IdentityPorts.Tokens tokens = mock(IdentityPorts.Tokens.class);
    when(tokens.access(any(), any())).thenReturn("jwt-access-token");

    google = mock(IdentityPorts.Google.class);
    accounts = mock(AccountApi.class);
    events = mock(EventPublisher.class);

    TransactionTemplate tx = mock(TransactionTemplate.class);
    when(tx.execute(any()))
        .thenAnswer(
            inv -> {
              TransactionCallback<?> cb = inv.getArgument(0);
              return cb.doInTransaction(mock(TransactionStatus.class));
            });

    AppProperties props = mock(AppProperties.class);
    AppProperties.Security sec = mock(AppProperties.Security.class);
    when(props.security()).thenReturn(sec);
    when(sec.refreshDays()).thenReturn(14);
    when(sec.accessMinutes()).thenReturn(10);
    when(sec.googleClientId()).thenReturn("client-id");

    service =
        new IdentityService(
            loginLimits,
            repository,
            passwords,
            tokens,
            google,
            props,
            mock(FieldCipher.class),
            Clock.fixed(now, ZoneOffset.UTC),
            mock(JobQueue.class),
            events,
            tx,
            accounts);
  }

  @Test
  @DisplayName("First-time Google Sign-Up creates verified passwordless user, links OAuth, and provisions profile")
  void googleSignUpNewUser() {
    when(google.verify("id-tok", "nonce-123"))
        .thenReturn(new IdentityPorts.Google.Verified("sub-new", "NewUser@Gmail.com", "Sahadat Hossain"));
    when(repository.oauthUser("GOOGLE", "sub-new")).thenReturn(Optional.empty());
    when(repository.byEmail("newuser@gmail.com")).thenReturn(Optional.empty());

    IdentityService.AuthResult result =
        service.googleAuth("id-tok", "nonce-123", "Chrome on Linux", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.accessToken()).isEqualTo("jwt-access-token");
    assertThat(result.refreshToken()).isNotBlank();
    verify(repository).consumeOAuthChallenge(IdempotentCommand.hash("nonce-123"), now);
    verify(repository).create(argThat(u -> u.email().equals("newuser@gmail.com") && u.passwordHash() == null && u.verified()));
    verify(repository).linkOAuth(any(), eq(result.userId()), eq("GOOGLE"), eq("sub-new"));
    verify(accounts).initializeProfile(result.userId(), "Sahadat Hossain");
    verify(events).publish(eq(result.userId()), eq("UserRegistered"), anyMap());
  }

  @Test
  @DisplayName("Returning Google user signs in directly without re-creating profile")
  void googleSignInReturningUser() {
    UUID existingId = UUID.randomUUID();
    User user = new User(existingId, "returning@gmail.com", null, now, "ACTIVE");

    when(google.verify("id-tok", null))
        .thenReturn(new IdentityPorts.Google.Verified("sub-ret", "returning@gmail.com", "Sahadat"));
    when(repository.oauthUser("GOOGLE", "sub-ret")).thenReturn(Optional.of(existingId));
    when(repository.lockUser(existingId)).thenReturn(user);

    IdentityService.AuthResult result =
        service.googleAuth("id-tok", null, "Chrome on macOS", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.userId()).isEqualTo(existingId);
    verify(repository, never()).create(any());
    verify(accounts, never()).initializeProfile(any(), any());
  }

  @Test
  @DisplayName("Existing email/password user links Google OAuth safely on first Google sign-in")
  void googleLinksExistingEmailUser() {
    UUID existingId = UUID.randomUUID();
    User existing = new User(existingId, "existing@gmail.com", "argon2-hash", now, "ACTIVE");

    when(google.verify("id-tok", null))
        .thenReturn(new IdentityPorts.Google.Verified("sub-link", "existing@gmail.com", "Sahadat"));
    when(repository.oauthUser("GOOGLE", "sub-link")).thenReturn(Optional.empty());
    when(repository.byEmail("existing@gmail.com")).thenReturn(Optional.of(existing));
    when(repository.lockUser(existingId)).thenReturn(existing);

    IdentityService.AuthResult result =
        service.googleAuth("id-tok", null, "Safari on iOS", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.userId()).isEqualTo(existingId);
    verify(repository, never()).create(any());
    verify(repository).linkOAuth(any(), eq(existingId), eq("GOOGLE"), eq("sub-link"));
    verify(events).publish(eq(existingId), eq("OAuthAccountLinked"), anyMap());
  }
}
