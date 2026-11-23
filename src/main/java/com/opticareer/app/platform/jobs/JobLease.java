package com.opticareer.app.platform.jobs;

import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.persistence.*;
import com.opticareer.app.shared.error.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobLease {
  private final JobJpaRepository jobs;
  private final DatabaseLocks locks;
  private final BlockedOwnerJpaRepository blocked;
  private final ErasureTombstoneJpaRepository tombstones;
  private static final ThreadLocal<Claim> CURRENT = new ThreadLocal<>();

  public JobLease(
      JobJpaRepository jobs,
      BlockedOwnerJpaRepository blocked,
      ErasureTombstoneJpaRepository tombstones,
      DatabaseLocks locks) {
    this.jobs = jobs;
    this.locks = locks;
    this.blocked = blocked;
    this.tombstones = tombstones;
  }

  void enter(UUID id, UUID token) {
    CURRENT.set(new Claim(id, token));
  }

  void leave() {
    CURRENT.remove();
  }

  @Transactional
  public void check() {
    Claim c = CURRENT.get();
    if (c != null && jobs.liveClaim(c.id(), c.token()).isEmpty())
      throw new DomainException(ErrorCode.PROVIDER_UNAVAILABLE, "The worker lease has expired.");
  }

  @Transactional
  public void requireOwner(UUID owner) {
    locks.exclusive("privacy-owner:" + owner);
    check();
    if (blocked.existsById(owner)
        || tombstones.existsById(IdempotentCommand.hash(owner.toString())))
      throw new DomainException(ErrorCode.DELETION_PENDING, "Account deletion is pending.");
  }

  record Claim(UUID id, UUID token) {}
}
