package com.opticareer.app.modules.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.identity.domain.IdentityRepository;
import com.opticareer.app.modules.identity.domain.Session;
import com.opticareer.app.modules.identity.domain.User;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.jobs.JobLease;
import com.opticareer.app.platform.jobs.JobQueue;
import com.opticareer.app.platform.mail.EmailService;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
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

class IdentityServiceFintechSecurityTest {

  private IdentityRepository repository;
  private IdentityPorts.Passwords passwords;
  private IdentityPorts.LoginLimits loginLimits;
  private IdentityPorts.Tokens tokens;
  private JobQueue jobs;
  private JobLease lease;
  private EventPublisher events;
  private TransactionTemplate tx;
  private AccountApi accounts;
  private AppProperties properties;
  private Clock clock;
  private EmailService emailService;
  private IdentityService service;

  private final Instant now = Instant.parse("2026-11-24T12:00:00Z");

  @BeforeEach
  void setUp() {
    repository = mock(IdentityRepository.class);
    passwords = mock(IdentityPorts.Passwords.class);
    loginLimits = mock(IdentityPorts.LoginLimits.class);
    tokens = mock(IdentityPorts.Tokens.class);
    jobs = mock(JobQueue.class);
    lease = mock(JobLease.class);
    events = mock(EventPublisher.class);
    tx = mock(TransactionTemplate.class);
    accounts = mock(AccountApi.class);
    emailService = mock(EmailService.class);
    FieldCipher cipher = mock(FieldCipher.class);
    clock = Clock.fixed(now, ZoneOffset.UTC);

    AppProperties.Security sec = new AppProperties.Security(
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "v1",
        false,
        "opticareer",
        null,
        10,
        14,
        false
    );
    properties = new AppProperties(
        "http://localhost:5173",
        "http://localhost:5173",
        sec,
        null,
        null,
        null,
        null,
        "no-reply@opticareer.com"
    );

    doAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    }).when(tx).execute(any());

    service = new IdentityService(
        loginLimits,
        repository,
        passwords,
        tokens,
        properties,
        cipher,
        clock,
        jobs,
        lease,
        events,
        tx,
        accounts,
        emailService
    );
  }

  @Test
  @DisplayName("Fintech RTR: Replaying an already-rotated refresh token triggers immediate user lockdown and security alert email")
  void refreshTokenReuseTriggersBreachLockdownAndSecurityAlert() {
    UUID owner = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    String rawToken = "stolen-already-used-refresh-token";
    String tokenHash = IdempotentCommand.hash(rawToken);

    // Session has already been rotated at an earlier timestamp (now minus 10 minutes)
    Session reusedSession = new Session(sessionId, owner, familyId, now.plusSeconds(86400), now.minusSeconds(600));

    when(repository.session(tokenHash)).thenReturn(Optional.of(reusedSession));
    User user = new User(owner, "victim@example.com", "hashedPassword", now, "ACTIVE");
    when(repository.byId(owner)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.refresh(rawToken))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.SESSION_REUSE);

    // Verify breach lockdown: all sessions across all devices revoked!
    verify(repository).revokeAll(owner);

    // Verify security alert email sent to victim's registered email
    verify(emailService).sendNotification(
        eq("victim@example.com"),
        contains("Security Alert"),
        contains("attempt to reuse an expired session token")
    );

    // Verify security event published for audit trail
    verify(events).publish(eq(owner), eq("SessionReuseDetected"), any());
  }

  @Test
  @DisplayName("Fintech Step-Up Auth: Reauthentication succeeds when caller provides valid master password")
  void reauthenticateSuccess() {
    UUID owner = UUID.randomUUID();
    User user = new User(owner, "user@example.com", "storedPasswordHash", now, "ACTIVE");

    when(repository.lockUser(owner)).thenReturn(user);
    when(passwords.matches("CorrectMasterPassword123!", "storedPasswordHash")).thenReturn(true);

    // Should complete cleanly without exception
    service.reauthenticate(owner, "CorrectMasterPassword123!", null);

    verify(lease).requireOwner(owner);
    verify(repository).lockUser(owner);
  }

  @Test
  @DisplayName("Fintech Step-Up Auth: Reauthentication throws 401 Unauthenticated on incorrect password")
  void reauthenticateFailsOnBadPassword() {
    UUID owner = UUID.randomUUID();
    User user = new User(owner, "user@example.com", "storedPasswordHash", now, "ACTIVE");

    when(repository.lockUser(owner)).thenReturn(user);
    when(passwords.matches("WrongPassword!", "storedPasswordHash")).thenReturn(false);

    assertThatThrownBy(() -> service.reauthenticate(owner, "WrongPassword!", null))
        .isInstanceOf(DomainException.class)
        .matches(e -> ((DomainException) e).code() == ErrorCode.UNAUTHENTICATED);
  }

  @Test
  @DisplayName("Fintech Device Management: Selective session revocation terminates targeted family")
  void selectiveSessionRevocation() {
    UUID owner = UUID.randomUUID();
    UUID targetFamily = UUID.randomUUID();

    service.revoke(owner, targetFamily);

    verify(lease).requireOwner(owner);
    verify(repository).revoke(targetFamily, owner);
  }
}
