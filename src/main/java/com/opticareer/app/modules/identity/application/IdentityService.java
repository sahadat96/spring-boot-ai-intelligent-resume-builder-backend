package com.opticareer.app.modules.identity.application;

import com.opticareer.app.modules.identity.IdentityApi;
import com.opticareer.app.modules.identity.domain.*;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.jobs.JobQueue;
import com.opticareer.app.platform.privacy.DataParticipant;
import com.opticareer.app.shared.error.*;
import com.opticareer.app.shared.event.EventPublisher;
import com.opticareer.app.shared.id.UuidV7;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdentityService  {
  private final IdentityRepository repository;
  private final IdentityPorts.Passwords passwords;
  private final IdentityPorts.Tokens tokens;
  private final IdentityPorts.Google google;
  private final AppProperties properties;
  private final FieldCipher cipher;
  private final Clock clock;
  private final JobQueue jobs;
  private final EventPublisher events;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;
  private final IdentityPorts.LoginLimits loginLimits;

  public IdentityService(
      IdentityPorts.LoginLimits loginLimits,
      IdentityRepository repository,
      IdentityPorts.Passwords passwords,
      IdentityPorts.Tokens tokens,
      IdentityPorts.Google google,
      AppProperties properties,
      FieldCipher cipher,
      Clock clock,
      JobQueue jobs,
      EventPublisher events,
      TransactionTemplate tx) {
    this.loginLimits = loginLimits;
    this.repository = repository;
    this.passwords = passwords;
    this.tokens = tokens;
    this.google = google;
    this.properties = properties;
    this.cipher = cipher;
    this.clock = clock;
    this.jobs = jobs;
    this.events = events;
    this.tx = tx;
    dummyHash = passwords.hash("timing-only-dummy-secret");
  }

  public void register(String email, String password) {
    PasswordPolicy.validate(password);
    passwords.checkBreached(password);
    String hash = passwords.hash(password);
    String normalized = email.toLowerCase(Locale.ROOT).strip();
    tx.executeWithoutResult(
        status -> {
            repository.lockEmail(normalized);
            UUID owner = UuidV7.next();
            var existing = repository.byEmail(normalized);
            if (existing.isPresent()) return;
            repository.create(new User(owner, normalized, hash, null, "ACTIVE"));
            emailToken(owner, normalized, "VERIFY");
            events.publish(owner, "UserRegistered", Map.of());
        });
  }

  private void emailToken(UUID owner, String email, String purpose) {
    String token = opaque();
    repository.token(
        UuidV7.next(),
        owner,
        IdempotentCommand.hash(token),
        purpose,
        clock.instant().plusSeconds(purpose.equals("VERIFY") ? 86400 : 1800));
    String path = purpose.equals("VERIFY") ? "/verify-email" : "/reset-password";
    jobs.enqueue(
        owner,
        "SEND_MAIL",
        Map.of(
            "email",
            email,
            "subject",
            purpose.equals("VERIFY")
                ? "Verify your OptiCareer email"
                : "Reset your OptiCareer password",
            "text",
            properties.publicUrl() + path + "?token=" + token));
  }

  private String opaque() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

}
