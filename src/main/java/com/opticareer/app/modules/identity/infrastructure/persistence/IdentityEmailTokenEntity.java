package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "email_tokens")
public class IdentityEmailTokenEntity extends JpaAudit {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID userId;

  @Column(nullable = false, length = 16)
  public String purpose;

  @Column(nullable = false, length = 64, unique = true)
  public String tokenHash;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant consumedAt;

  public IdentityEmailTokenEntity() {}
}
