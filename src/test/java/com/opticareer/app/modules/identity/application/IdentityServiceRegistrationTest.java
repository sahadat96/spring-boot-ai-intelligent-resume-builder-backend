package com.opticareer.app.modules.identity.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.identity.domain.IdentityRepository;
import com.opticareer.app.modules.identity.domain.User;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.jobs.JobQueue;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.event.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

class IdentityServiceRegistrationTest {

  private IdentityRepository repository;
  private IdentityPorts.Passwords passwords;
  private IdentityPorts.LoginLimits loginLimits;
  private JobQueue jobs;
  private EventPublisher events;
  private TransactionTemplate tx;
  private AccountApi accounts;
  private AppProperties properties;
  private Clock clock;
  private IdentityService service;

  @BeforeEach
  void setUp() {
    repository = mock(IdentityRepository.class);
    passwords = mock(IdentityPorts.Passwords.class);
    loginLimits = mock(IdentityPorts.LoginLimits.class);
    jobs = mock(JobQueue.class);
    events = mock(EventPublisher.class);
    tx = mock(TransactionTemplate.class);
    accounts = mock(AccountApi.class);
    FieldCipher cipher = mock(FieldCipher.class);
    clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

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

    when(passwords.hash(anyString())).thenReturn("hashed-bcrypt-value");

    // Make tx.executeWithoutResult invoke the callback immediately
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
        null,
        properties,
        cipher,
        clock,
        jobs,
        events,
        tx,
        accounts
    );
  }

  @Test
  @DisplayName("Successful registration creates identity user, initializes account profile, saves token, enqueues verification email, and publishes outbox event")
  void successfulRegistration() {
    String fullName = "  Emran Hossain  ";
    String email = "Candidate@Example.Com ";
    String password = "CorrectHorseBatteryStaple123!";

    when(repository.byEmail("candidate@example.com")).thenReturn(Optional.empty());

    service.register(fullName, email, password);

    // Verify email lock is obtained
    verify(repository).lockEmail("candidate@example.com");

    // Verify user is created in identity schema without fullName (auth only)
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(repository).create(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.email()).isEqualTo("candidate@example.com");
    assertThat(created.passwordHash()).isEqualTo("hashed-bcrypt-value");
    assertThat(created.status()).isEqualTo("ACTIVE");
    assertThat(created.verifiedAt()).isNull();

    // Verify profile is initialized in account module/schema with full name
    verify(accounts).initializeProfile(created.id(), "Emran Hossain");

    // Verify verification token is persisted with 24-hour expiration
    verify(repository).token(
        any(UUID.class),
        eq(created.id()),
        anyString(),
        eq("VERIFY"),
        eq(clock.instant().plusSeconds(86400))
    );

    // Verify email verification job is enqueued
    verify(jobs).enqueue(
        eq(created.id()),
        eq("SEND_MAIL"),
        argThat(payload -> {
          if (!(payload instanceof Map<?, ?> map)) return false;
          return "candidate@example.com".equals(map.get("email"))
              && "Verify your OptiCareer email".equals(map.get("subject"))
              && map.get("text").toString().contains("Your 6-digit verification code to verify your account is:");
        })
    );

    // Verify domain outbox event is published with full name
    verify(events).publish(eq(created.id()), eq("UserRegistered"), eq(Map.of("fullName", "Emran Hossain")));
  }

  @Test
  @DisplayName("Registration is idempotent: existing email silently returns without creating duplicate user or leaking presence")
  void idempotentDuplicateRegistration() {
    String fullName = "Emran Hossain";
    String email = "existing@example.com";
    String password = "CorrectHorseBatteryStaple123!";

    User existing = new User(UUID.randomUUID(), email, "existing-hash", Instant.now(), "ACTIVE");
    when(repository.byEmail(email)).thenReturn(Optional.of(existing));

    assertThatCode(() -> service.register(fullName, email, password)).doesNotThrowAnyException();

    verify(repository).lockEmail(email);
    verify(repository, never()).create(any());
    verify(accounts, never()).initializeProfile(any(), any());
    verify(repository, never()).token(any(), any(), any(), any(), any());
    verify(jobs, never()).enqueue(any(), any(), any());
    verify(events, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("Registration rejects invalid or blank full names before database operations")
  void rejectsInvalidFullName() {
    assertThatThrownBy(() -> service.register(null, "user@example.com", "CorrectHorseBatteryStaple123!"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("Provide a full name between 2 and 150 characters.");

    assertThatThrownBy(() -> service.register("   ", "user@example.com", "CorrectHorseBatteryStaple123!"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("Provide a full name between 2 and 150 characters.");

    assertThatThrownBy(() -> service.register("A", "user@example.com", "CorrectHorseBatteryStaple123!"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("Provide a full name between 2 and 150 characters.");

    assertThatThrownBy(() -> service.register("A".repeat(151), "user@example.com", "CorrectHorseBatteryStaple123!"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("Provide a full name between 2 and 150 characters.");

    verifyNoInteractions(repository);
    verifyNoInteractions(accounts);
    verifyNoInteractions(jobs);
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("Registration rejects invalid password before database operations")
  void rejectsWeakPassword() {
    assertThatThrownBy(() -> service.register("Emran Hossain", "user@example.com", "weak"))
        .isInstanceOf(DomainException.class);

    verifyNoInteractions(repository);
    verifyNoInteractions(accounts);
    verifyNoInteractions(jobs);
    verifyNoInteractions(events);
  }
}
