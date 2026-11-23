package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "session_families")
public class IdentityFamilyEntity extends JpaAudit {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID userId;

  public Instant revokedAt;

  @Column(nullable = false)
  public Instant expiresAt;

  @Column(length = 150)
  public String deviceLabel;

  @Column(length = 64)
  public String ipHash;

  @Column(length = 64)
  public String userAgentHash;

  public IdentityFamilyEntity() {}
}
