package com.opticareer.app.modules.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.identity.domain.IdentityRepository;
import com.opticareer.app.modules.identity.domain.User;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.jobs.JobLease;
import com.opticareer.app.platform.jobs.JobQueue;
import com.opticareer.app.platform.mail.EmailService;
import com.opticareer.app.shared.event.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class IdentityServiceVerificationTest {

  private IdentityRepository repository;
  private IdentityPorts.Passwords passwords;
  private IdentityPorts.LoginLimits loginLimits;
  private JobQueue jobs;
  private JobLease lease;
  private EventPublisher events;
  private TransactionTemplate tx;
  private AccountApi accounts;
  private AppProperties properties;
  private Clock clock;
  private EmailService emailService;
  private IdentityService service;

  @BeforeEach
  void setUp() {
    repository = mock(IdentityRepository.class);
    passwords = mock(IdentityPorts.Passwords.class);
    loginLimits = mock(IdentityPorts.LoginLimits.class);
    jobs = mock(JobQueue.class);
    lease = mock(JobLease.class);
    events = mock(EventPublisher.class);
    tx = mock(TransactionTemplate.class);
    accounts = mock(AccountApi.class);
    emailService = mock(EmailService.class);
    FieldCipher cipher = mock(FieldCipher.class);
    clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);

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
      Consumer<TransactionStatus> callback = invocation.getArgument(0);
      callback.accept(mock(TransactionStatus.class));
      return null;
    }).when(tx).executeWithoutResult(any());

    service = new IdentityService(
        loginLimits,
        repository,
        passwords,
        null,
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
  @DisplayName("verifyEmail successfully verifies OTP and calls lease.requireOwner without StackOverflowError")
  void verifyEmailCallsLeaseRequireOwner() {
    UUID ownerId = UUID.randomUUID();
    String email = "candidate@example.com";
    String otp = "123456";
    String saltedHash = IdempotentCommand.hash(email + ":" + otp);

    when(repository.tokenOwner(eq(saltedHash), eq("VERIFY"), any(Instant.class)))
        .thenReturn(Optional.of(ownerId));
    when(repository.lockUser(ownerId))
        .thenReturn(new User(ownerId, email, "hash", null, "ACTIVE"));
    when(repository.consume(eq(saltedHash), eq("VERIFY"), any(Instant.class)))
        .thenReturn(ownerId);

    // Call verifyEmail - should not throw StackOverflowError
    service.verifyEmail(email, otp);

    // Verify lease.requireOwner was called exactly once on lease
    verify(lease).requireOwner(ownerId);
    verify(repository).verify(ownerId);
    verify(repository).invalidateTokens(eq(ownerId), eq("VERIFY"), any(Instant.class));
    verify(events).publish(eq(ownerId), eq("EmailVerified"), any());
  }
}
