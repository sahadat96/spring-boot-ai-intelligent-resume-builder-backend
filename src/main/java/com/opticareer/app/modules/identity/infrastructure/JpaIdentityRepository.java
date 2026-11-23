package com.opticareer.app.modules.identity.infrastructure;

import com.opticareer.app.modules.identity.domain.*;
import com.opticareer.app.modules.identity.infrastructure.persistence.*;
import com.opticareer.app.platform.persistence.DatabaseLocks;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaIdentityRepository implements IdentityRepository {
  private final IdentityUserJpaRepository users;
  private final IdentityCredentialJpaRepository credentials;
  private final IdentityEmailTokenJpaRepository tokens;
  private final DatabaseLocks locks;
  private final Clock clock;

  public JpaIdentityRepository(
      IdentityUserJpaRepository users,
      IdentityCredentialJpaRepository credentials,
      IdentityEmailTokenJpaRepository tokens,
      DatabaseLocks locks,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.tokens = tokens;
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
    // Parent row must exist before independently mapped credential inserts.
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
  public void token(UUID id, UUID owner, String hash, String purpose, Instant expires) {
    IdentityEmailTokenEntity e = new IdentityEmailTokenEntity();
    e.id = id;
    e.userId = owner;
    e.tokenHash = hash;
    e.purpose = purpose;
    e.expiresAt = expires;
    tokens.save(e);
  }
}
