package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "sessions")
public class IdentitySessionEntity extends JpaAudit {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID userId;

  @Column(nullable = false)
  public UUID familyId;

  @Column(nullable = false, length = 64, unique = true)
  public String tokenHash;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant rotatedAt;

  public IdentitySessionEntity() {}
}
