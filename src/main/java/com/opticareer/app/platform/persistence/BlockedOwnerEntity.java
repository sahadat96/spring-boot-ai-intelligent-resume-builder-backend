package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "PlatformBlockedOwner")
@Table(name = "blocked_owners", schema = "platform")
public class BlockedOwnerEntity {
  @Id
  @Column(name = "owner_id")
  public UUID ownerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  public BlockedOwnerEntity() {}

  public BlockedOwnerEntity(UUID ownerId) {
    this.ownerId = ownerId;
  }

  @PrePersist
  protected void created() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
