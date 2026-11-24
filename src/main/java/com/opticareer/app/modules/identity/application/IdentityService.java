package com.opticareer.app.modules.identity.application;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.identity.IdentityApi;
import com.opticareer.app.modules.identity.domain.*;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.jobs.JobLease;
import com.opticareer.app.platform.jobs.JobQueue;
import com.opticareer.app.platform.mail.EmailService;
import com.opticareer.app.platform.mail.EmailTemplates;
import com.opticareer.app.platform.mail.MailPort;
import com.opticareer.app.platform.privacy.DataParticipant;
import com.opticareer.app.shared.error.*;
import com.opticareer.app.shared.event.EventPublisher;
import com.opticareer.app.shared.id.UuidV7;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdentityService implements IdentityApi, DataParticipant {

  private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

  private final IdentityRepository repository;
  private final IdentityPorts.Passwords passwords;
  private final IdentityPorts.Tokens tokens;
  private final AppProperties properties;
  private final FieldCipher cipher;
  private final Clock clock;
  private final JobQueue jobs;
  private final JobLease lease;
  private final EventPublisher events;
  private final TransactionTemplate tx;
  private final AccountApi accounts;
  private final EmailService emailService;
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;
  private final IdentityPorts.LoginLimits loginLimits;

  @Autowired
  public IdentityService(
      IdentityPorts.LoginLimits loginLimits,
      IdentityRepository repository,
      IdentityPorts.Passwords passwords,
      @Nullable IdentityPorts.Tokens tokens,
      AppProperties properties,
      FieldCipher cipher,
      Clock clock,
      JobQueue jobs,
      @Nullable JobLease lease,
      EventPublisher events,
      TransactionTemplate tx,
      AccountApi accounts,
      @Nullable EmailService emailService) {
    this.loginLimits = loginLimits;
    this.repository = repository;
    this.passwords = passwords;
    this.tokens = tokens;
    this.properties = properties;
    this.cipher = cipher;
    this.clock = clock;
    this.jobs = jobs;
    this.lease = lease;
    this.events = events;
    this.tx = tx;
    this.accounts = accounts;
    this.emailService = emailService;
    this.dummyHash = passwords.hash("timing-only-dummy-secret");
  }

  public IdentityService(
      IdentityPorts.LoginLimits loginLimits,
      IdentityRepository repository,
      IdentityPorts.Passwords passwords,
      @Nullable IdentityPorts.Tokens tokens,
      AppProperties properties,
      FieldCipher cipher,
      Clock clock,
      JobQueue jobs,
      @Nullable JobLease lease,
      EventPublisher events,
      TransactionTemplate tx,
      AccountApi accounts,
      @Nullable MailPort mail) {
    this(
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
        mail != null ? new com.opticareer.app.platform.mail.DefaultEmailService(mail) : null);
  }

  public IdentityService(
      IdentityPorts.LoginLimits loginLimits,
      IdentityRepository repository,
      IdentityPorts.Passwords passwords,
      @Nullable IdentityPorts.Tokens tokens,
      @Nullable IdentityPorts.Google google,
      AppProperties properties,
      FieldCipher cipher,
      Clock clock,
      JobQueue jobs,
      EventPublisher events,
      TransactionTemplate tx,
      AccountApi accounts) {
    this(
        loginLimits,
        repository,
        passwords,
        tokens,
        properties,
        cipher,
        clock,
        jobs,
        null,
        events,
        tx,
        accounts,
        (EmailService) null);
  }

  public void register(String fullName, String email, String password) {
    if (fullName == null || fullName.strip().length() < 2 || fullName.strip().length() > 150) {
      throw DomainException.invalid("Provide a full name between 2 and 150 characters.");
    }
    String cleanName = fullName.strip();
    PasswordPolicy.validate(password);
    passwords.checkBreached(password);
    String hash = passwords.hash(password);
    String normalized = email.toLowerCase(Locale.ROOT).strip();

    tx.executeWithoutResult(
        status -> {
          repository.lockEmail(normalized);
          UUID owner = UuidV7.next();
          var existing = repository.byEmail(normalized);
          if (existing.isPresent()) {
            return;
          }
          repository.create(new User(owner, normalized, hash, null, "ACTIVE"));
          if (accounts != null) {
            accounts.initializeProfile(owner, cleanName);
          }
          sendOtp(owner, normalized, "VERIFY");
          events.publish(owner, "UserRegistered", Map.of("fullName", cleanName));
        });
  }

  public void verifyEmail(String email, String otpOrToken) {
    String normalized = email != null ? email.toLowerCase(Locale.ROOT).strip() : null;
    if (normalized != null) {
      loginLimits.attempt("otp:" + normalized);
    }
    tx.executeWithoutResult(
        status -> {
          UUID owner = consumeToken(normalized, otpOrToken, "VERIFY");
          repository.verify(owner);
          repository.invalidateTokens(owner, "VERIFY", clock.instant());
          events.publish(owner, "EmailVerified", Map.of());
        });
  }

  public void resendVerification(String email) {
    String normalized = email.toLowerCase(Locale.ROOT).strip();
    loginLimits.verification(normalized);
    tx.executeWithoutResult(
        status ->
            repository
                .byEmail(normalized)
                .filter(u -> u.active() && !u.verified())
                .ifPresent(
                    u -> {
                      User current = repository.lockUser(u.id());
                      if (current.active() && !current.verified()) {
                        repository.invalidateTokens(current.id(), "VERIFY", clock.instant());
                        sendOtp(current.id(), current.email(), "VERIFY");
                      }
                    }));
  }

  public void forgotPassword(String email) {
    String normalized = email.toLowerCase(Locale.ROOT).strip();
    loginLimits.verification("forgot:" + normalized);
    tx.executeWithoutResult(
        status ->
            repository
                .byEmail(normalized)
                .filter(User::active)
                .ifPresent(
                    u -> {
                      User current = repository.lockUser(u.id());
                      if (current.active()) {
                        repository.invalidateTokens(current.id(), "RESET", clock.instant());
                        sendOtp(current.id(), current.email(), "RESET");
                      }
                    }));
  }

  public void resetPassword(String email, String otpOrToken, String newPassword) {
    String normalized = email != null ? email.toLowerCase(Locale.ROOT).strip() : null;
    PasswordPolicy.validate(newPassword);
    passwords.checkBreached(newPassword);
    String hash = passwords.hash(newPassword);

    tx.executeWithoutResult(
        status -> {
          UUID owner = consumeToken(normalized, otpOrToken, "RESET");
          repository.password(owner, hash);
          repository.invalidateTokens(owner, "RESET", clock.instant());
          repository.revokeAll(owner);
          events.publish(owner, "PasswordChanged", Map.of());
        });
  }

  public AuthResult login(
      String email, String password, String device, String ip, String agent) {
    String normalized = email.toLowerCase(Locale.ROOT).strip();
    loginLimits.attempt(normalized);
    User found = repository.byEmail(normalized).orElse(null);

    boolean matches =
        passwords.matches(
            password,
            found == null || found.passwordHash() == null ? dummyHash : found.passwordHash());
    if (!matches || found == null) {
      throw invalidLogin();
    }

    return tx.execute(
        status -> {
          requireOwner(found.id());
          User current = repository.lockUser(found.id());
          if (!Objects.equals(current.passwordHash(), found.passwordHash())) {
            throw invalidLogin();
          }
          requireActive(current);
          if (!current.verified()) {
            throw new DomainException(
                ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email before signing in.");
          }
          return newFamily(current.id(), device, ip, agent);
        });
  }

  public AuthResult refresh(String token) {
    if (token == null || token.isBlank() || token.length() > 512) {
      throw new DomainException(ErrorCode.INVALID_TOKEN, "Refresh token is missing or invalid.");
    }
    String hash = IdempotentCommand.hash(token);
    RefreshOutcome outcome =
        tx.execute(
            status -> {
              Session candidate =
                  repository
                      .session(hash)
                      .orElseThrow(
                          () -> new DomainException(ErrorCode.INVALID_TOKEN, "Invalid refresh token."));

              requireOwner(candidate.owner());
              repository.lockFamily(candidate.family());
              Session session = repository.session(hash).orElseThrow();

              if (session.rotatedAt() != null) {
                // Token reuse detected! In fintech standards, lockdown ALL sessions across all devices immediately!
                repository.revokeAll(session.owner());
                events.publish(
                    session.owner(), "SessionReuseDetected", Map.of("familyId", session.family()));
                return new RefreshOutcome(null, true, session.owner());
              }

              if (!session.expiresAt().isAfter(clock.instant())
                  || !repository.familyActive(session.family(), session.owner())) {
                throw new DomainException(ErrorCode.INVALID_TOKEN, "Session expired or revoked.");
              }

              repository.rotate(session.id());
              String nextRefresh = opaque();
              Instant nextExpiry = session.expiresAt();

              repository.session(
                  new Session(UuidV7.next(), session.owner(), session.family(), nextExpiry, null),
                  IdempotentCommand.hash(nextRefresh));

              return new RefreshOutcome(
                  new AuthResult(
                      tokens.access(session.owner(), session.family()),
                      nextRefresh,
                      session.owner(),
                      properties.security().accessMinutes() * 60),
                  false,
                  session.owner());
            });

    if (Objects.requireNonNull(outcome).reused()) {
      if (emailService != null && outcome.owner() != null) {
        repository.byId(outcome.owner()).ifPresent(user -> {
          emailService.sendNotification(
              user.email(),
              "Security Alert: Suspicious session activity detected",
              "We detected an attempt to reuse an expired session token on your OptiCareer account. As a strict security precaution, all active sessions on all devices have been terminated. If this was not you, please sign in and change your password immediately."
          );
        });
      }
      throw new DomainException(
          ErrorCode.SESSION_REUSE, "Refresh token reuse detected. Please sign in again.");
    }
    return outcome.result();
  }

  @Transactional
  public void logout(String token) {
    if (token != null && !token.isBlank()) {
      repository
          .session(IdempotentCommand.hash(token))
          .ifPresent(s -> repository.revoke(s.family(), s.owner()));
    }
  }

  @Transactional
  public void logoutAll(UUID owner) {
    requireOwner(owner);
    repository.revokeAll(owner);
    events.publish(owner, "SessionsRevoked", Map.of());
  }

  @Transactional
  public void revoke(UUID owner, UUID family) {
    requireOwner(owner);
    repository.revoke(family, owner);
  }

  public List<Map<String, Object>> sessions(UUID owner) {
    return repository.families(owner);
  }

  @Override
  public UserSummary user(UUID owner) {
    User u = repository.byId(owner).orElseThrow(() -> DomainException.missing("User"));
    return new UserSummary(u.id(), u.email(), u.verified(), u.status());
  }

  @Override
  public void requireSession(UUID owner, UUID family) {
    if (!repository.familyActive(family, owner)) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Session expired or revoked.");
    }
  }

  @Override
  @Transactional
  public void block(UUID owner) {
    requireOwner(owner);
    repository.lockUser(owner);
    repository.block(owner);
  }

  @Override
  @Transactional
  public void reauthenticate(UUID owner, String password, String otp) {
    requireOwner(owner);
    User u = repository.lockUser(owner);
    requireActive(u);
    if (u.passwordHash() == null || !passwords.matches(password, u.passwordHash())) {
      throw invalidLogin();
    }
  }

  @Override
  public List<UUID> activeUsers(UUID cursor, int size) {
    return repository.activeUsers(cursor, Math.min(100, size));
  }

  @Override
  public List<UUID> recoveryUsers(UUID cursor, int size) {
    return repository.recoveryUsers(cursor, Math.min(100, size));
  }

  @Override
  public boolean isAdmin(UUID owner) {
    return repository.isAdmin(owner);
  }

  @Override
  public String module() {
    return "identity";
  }

  @Override
  public Object exportData(UUID owner) {
    return Map.of("user", user(owner), "sessions", sessions(owner));
  }

  @Override
  @Transactional
  public void eraseData(UUID owner) {
    repository.erase(owner);
  }

  private UUID consumeToken(@Nullable String email, String otpOrToken, String purpose) {
    Instant now = clock.instant();
    String candidate = otpOrToken.trim();
    UUID owner = null;

    // Check email-salted hash first (for 6-digit OTPs)
    if (email != null && !email.isBlank()) {
      String saltedHash = IdempotentCommand.hash(email.toLowerCase(Locale.ROOT).strip() + ":" + candidate);
      owner = repository.tokenOwner(saltedHash, purpose, now).orElse(null);
      if (owner != null) {
        requireOwner(owner);
        requireActive(repository.lockUser(owner));
        return repository.consume(saltedHash, purpose, now);
      }
    }

    // Direct token hash check
    String directHash = IdempotentCommand.hash(candidate);
    owner = repository.tokenOwner(directHash, purpose, now).orElse(null);
    if (owner != null) {
      requireOwner(owner);
      requireActive(repository.lockUser(owner));
      return repository.consume(directHash, purpose, now);
    }

    throw new DomainException(ErrorCode.INVALID_TOKEN, "Invalid or expired verification code.");
  }

  private AuthResult newFamily(UUID owner, String device, String ip, String agent) {
    UUID family = UuidV7.next();
    Instant expires = clock.instant().plus(Duration.ofDays(properties.security().refreshDays()));
    String refresh = opaque();

    repository.family(
        family,
        owner,
        expires,
        device == null ? "Browser" : device.substring(0, Math.min(device.length(), 150)),
        IdempotentCommand.hash(Objects.toString(ip, "")),
        IdempotentCommand.hash(Objects.toString(agent, "")));

    repository.session(
        new Session(UuidV7.next(), owner, family, expires, null), IdempotentCommand.hash(refresh));

    events.publish(owner, "UserSignedIn", Map.of("familyId", family));
    return new AuthResult(
        tokens.access(owner, family), refresh, owner, properties.security().accessMinutes() * 60);
  }

  private void sendOtp(UUID owner, String email, String purpose) {
    // Generate secure 6-digit numeric OTP (100000 - 999999)
    String otp = String.format("%06d", random.nextInt(900_000) + 100_000);
    // Salt hash with normalized email to ensure global uniqueness in email_tokens
    String hash = IdempotentCommand.hash(email + ":" + otp);
    // Verification code valid for 24h, reset OTP valid for 15 minutes
    Instant expires = clock.instant().plusSeconds(purpose.equals("VERIFY") ? 86400 : 900);

    repository.token(UuidV7.next(), owner, hash, purpose, expires);

    String subject =
        purpose.equals("VERIFY")
            ? "Verify your OptiCareer email"
            : "Reset your OptiCareer password";

    String body =
        purpose.equals("VERIFY")
            ? EmailTemplates.verificationOtpPlainText(otp)
            : EmailTemplates.passwordResetOtpPlainText(otp);

    // 1. Transactional outbox job
    jobs.enqueue(
        owner,
        "SEND_MAIL",
        Map.of(
            "email", email,
            "subject", subject,
            "text", body,
            "otp", otp));

    // 2. Dispatch via decoupled EmailService
    if (emailService != null) {
      if ("VERIFY".equals(purpose)) {
        emailService.sendVerificationOtp(email, otp);
      } else {
        emailService.sendPasswordResetOtp(email, otp);
      }
    }
  }

  private String opaque() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private void requireOwner(UUID owner) {
    if (lease != null) {
      lease.requireOwner(owner);
    }
  }

  private void requireActive(User user) {
    if (!user.active()) {
      throw new DomainException(ErrorCode.DELETION_PENDING, "Account deletion is pending.");
    }
  }

  private DomainException invalidLogin() {
    return new DomainException(
        ErrorCode.UNAUTHENTICATED, "Invalid email or password.");
  }

  public record AuthResult(String accessToken, String refreshToken, UUID userId, int expiresIn) {}

  private record RefreshOutcome(AuthResult result, boolean reused, UUID owner) {}
}
