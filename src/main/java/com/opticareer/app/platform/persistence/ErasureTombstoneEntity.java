package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "PlatformErasureTombstone")
@Table(name = "erasure_tombstones", schema = "platform")
public class ErasureTombstoneEntity {
  @Id
  @Column(name = "owner_hash", length = 64)
  public String ownerHash;

  @Column(name = "audit_id", nullable = false)
  public UUID auditId;

  @Column(name = "completed_at", nullable = false)
  public Instant completedAt;

  public ErasureTombstoneEntity() {}

  @PrePersist
  protected void completed() {
    if (completedAt == null) completedAt = Instant.now();
  }
}
