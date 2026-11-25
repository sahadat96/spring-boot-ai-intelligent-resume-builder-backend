package com.opticareer.app.modules.identity.infrastructure;

import com.opticareer.app.modules.identity.domain.*;
import com.opticareer.app.modules.identity.infrastructure.persistence.*;
import com.opticareer.app.platform.persistence.DatabaseLocks;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaIdentityRepository implements IdentityRepository {
  private final IdentityUserJpaRepository users;
  private final IdentityCredentialJpaRepository credentials;
  private final IdentityFamilyJpaRepository families;
  private final IdentitySessionJpaRepository sessions;
  private final IdentityEmailTokenJpaRepository tokens;
  private final IdentityOAuthAccountJpaRepository oauthAccounts;
  private final IdentityOAuthChallengeJpaRepository oauthChallenges;
  private final DatabaseLocks locks;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public JpaIdentityRepository(
      IdentityUserJpaRepository users,
      IdentityCredentialJpaRepository credentials,
      IdentityFamilyJpaRepository families,
      IdentitySessionJpaRepository sessions,
      IdentityEmailTokenJpaRepository tokens,
      IdentityOAuthAccountJpaRepository oauthAccounts,
      IdentityOAuthChallengeJpaRepository oauthChallenges,
      DatabaseLocks locks,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.families = families;
    this.sessions = sessions;
    this.tokens = tokens;
    this.oauthAccounts = oauthAccounts;
    this.oauthChallenges = oauthChallenges;
    this.locks = locks;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void lockEmail(String email) {
    locks.exclusive("identity-email:" + email);
  }

  @Override
  public Optional<User> byEmail(String email) {
    return users.findByEmail(email).map(this::domain);
  }

  @Override
  public Optional<User> byId(UUID id) {
    return users.findById(id).map(this::domain);
  }

  @Override
  @Transactional
  public User lockUser(UUID id) {
    return domain(
        users
            .lock(id)
            .orElseThrow(
                () -> new DomainException(ErrorCode.UNAUTHENTICATED, "Account unavailable.")));
  }

  private User domain(IdentityUserEntity u) {
    String hash = credentials.findById(u.id).map(c -> c.passwordHash).orElse(null);
    return new User(u.id, u.email, hash, u.emailVerifiedAt, u.status);
  }

  @Override
  @Transactional
  public void create(User u) {
    IdentityUserEntity e = new IdentityUserEntity();
    e.id = u.id();
    e.email = u.email();
    e.emailVerifiedAt = u.verifiedAt();
    e.status = u.status();
    users.saveAndFlush(e);
    if (u.passwordHash() != null) {
      password(u.id(), u.passwordHash());
    }
  }

  @Override
  @Transactional
  public void password(UUID owner, String hash) {
    IdentityCredentialEntity e =
        credentials.findById(owner).orElseGet(IdentityCredentialEntity::new);
    e.userId = owner;
    e.passwordHash = hash;
    credentials.save(e);
  }

  @Override
  @Transactional
  public void verify(UUID owner) {
    users
        .findById(owner)
        .ifPresent(
            e -> {
              e.emailVerifiedAt = clock.instant();
              users.save(e);
            });
  }

  @Override
  @Transactional
  public void block(UUID owner) {
    users
        .findById(owner)
        .ifPresent(
            e -> {
              e.status = "PENDING_DELETE";
              users.save(e);
            });
    revokeAll(owner);
  }

  @Override
  @Transactional
  public void family(UUID id, UUID owner, Instant expires, String device, String ip, String agent) {
    IdentityFamilyEntity e = new IdentityFamilyEntity();
    e.id = id;
    e.userId = owner;
    e.expiresAt = expires;
    e.deviceLabel = device;
    e.ipHash = ip;
    e.userAgentHash = agent;
    families.saveAndFlush(e);
  }

  @Override
  public boolean familyActive(UUID family, UUID owner) {
    return families.activeCount(family, owner, clock.instant()) == 1;
  }

  @Override
  @Transactional
  public void lockFamily(UUID family) {
    families.lock(family);
  }

  @Override
  @Transactional
  public void revoke(UUID family, UUID owner) {
    families
        .lock(family)
        .filter(e -> e.userId.equals(owner))
        .ifPresent(
            e -> {
              e.revokedAt = clock.instant();
              families.save(e);
            });
  }

  @Override
  @Transactional
  public void revokeAll(UUID owner) {
    for (IdentityFamilyEntity e : families.activeLocked(owner)) {
      e.revokedAt = clock.instant();
      families.save(e);
    }
  }

  @Override
  @Transactional
  public void session(Session s, String hash) {
    IdentitySessionEntity e = new IdentitySessionEntity();
    e.id = s.id();
    e.userId = s.owner();
    e.familyId = s.family();
    e.tokenHash = hash;
    e.expiresAt = s.expiresAt();
    e.rotatedAt = s.rotatedAt();
    sessions.save(e);
  }

  @Override
  public Optional<Session> session(String hash) {
    return sessions.token(hash);
  }

  @Override
  @Transactional
  public void rotate(UUID id) {
    sessions
        .findById(id)
        .ifPresent(
            e -> {
              e.rotatedAt = clock.instant();
              sessions.saveAndFlush(e);
            });
  }

  @Override
  public List<Map<String, Object>> families(UUID owner) {
    return families.findByUserIdOrderByCreatedAtDesc(owner, PageRequest.of(0, 100)).stream()
        .map(
            e -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", e.id);
              m.put("deviceLabel", e.deviceLabel);
              m.put("createdAt", e.createdAt);
              m.put("expiresAt", e.expiresAt);
              m.put("revoked", e.revokedAt != null);
              return m;
            })
        .toList();
  }

  @Override
  @Transactional
  public void token(UUID id, UUID owner, String hash, String purpose, Instant expires) {
    IdentityEmailTokenEntity e = new IdentityEmailTokenEntity();
    e.id = id;
    e.userId = owner;
    e.tokenHash = hash;
    e.purpose = purpose;
    e.expiresAt = expires;
    tokens.save(e);
  }

  @Override
  public Optional<UUID> tokenOwner(String hash, String purpose, Instant now) {
    return tokens.tokenOwner(hash, purpose, now);
  }

  @Override
  @Transactional
  public void invalidateTokens(UUID owner, String purpose, Instant now) {
    for (IdentityEmailTokenEntity token : tokens.pending(owner, purpose)) {
      token.consumedAt = now;
    }
    tokens.flush();
  }

  @Override
  @Transactional
  public UUID consume(String hash, String purpose, Instant now) {
    IdentityEmailTokenEntity e =
        tokens
            .lockToken(hash, purpose)
            .filter(t -> t.consumedAt == null && t.expiresAt.isAfter(now))
            .orElseThrow(
                () -> new DomainException(ErrorCode.INVALID_TOKEN, "Invalid or expired verification code."));
    e.consumedAt = now;
    tokens.saveAndFlush(e);
    return e.userId;
  }

  @Override
  public List<UUID> activeUsers(UUID cursor, int size) {
    PageRequest page = PageRequest.of(0, Math.max(1, Math.min(100, size)));
    return cursor == null ? users.active(page) : users.activeAfter(cursor, page);
  }

  @Override
  public List<UUID> recoveryUsers(UUID cursor, int size) {
    PageRequest page = PageRequest.of(0, Math.max(1, Math.min(100, size)));
    return cursor == null ? users.recovery(page) : users.recoveryAfter(cursor, page);
  }

  @Override
  public boolean isAdmin(UUID owner) {
    return users.existsByIdAndRoleAndStatus(owner, "ADMIN", "ACTIVE");
  }

  @Override
  @Transactional
  public void erase(UUID owner) {
    users.deleteById(owner);
    users.flush();
  }

  @Override
  public Optional<UUID> oauthUser(String provider, String subject) {
    return oauthAccounts.findByProviderAndSubject(provider, subject).map(a -> a.userId);
  }

  @Override
  @Transactional
  public void linkOAuth(UUID id, UUID owner, String provider, String subject) {
    if (oauthAccounts.findByProviderAndSubject(provider, subject).isPresent()) {
      return;
    }
    var entity = new IdentityOAuthAccountEntity();
    entity.id = id;
    entity.userId = owner;
    entity.provider = provider;
    entity.subject = subject;
    oauthAccounts.saveAndFlush(entity);
  }

  @Override
  @Transactional
  public void recordOAuthChallenge(String tokenHash, Instant expiresAt) {
    var entity = new IdentityOAuthChallengeEntity();
    entity.tokenHash = tokenHash;
    entity.expiresAt = expiresAt;
    entity.createdAt = clock.instant();
    oauthChallenges.saveAndFlush(entity);
  }

  @Override
  @Transactional
  public void consumeOAuthChallenge(String tokenHash, Instant now) {
    var entity =
        oauthChallenges
            .lock(tokenHash)
            .filter(c -> c.consumedAt == null && c.expiresAt.isAfter(now))
            .orElseThrow(
                () -> new DomainException(ErrorCode.INVALID_TOKEN, "Invalid or expired OAuth challenge."));
    entity.consumedAt = now;
    oauthChallenges.saveAndFlush(entity);
  }
}
