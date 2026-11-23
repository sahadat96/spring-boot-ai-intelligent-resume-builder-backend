package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "credentials")
public class IdentityCredentialEntity extends JpaAudit {
  @Id public UUID userId;

  @Column(nullable = false, length = 100)
  public String passwordHash;

  public IdentityCredentialEntity() {}
}
