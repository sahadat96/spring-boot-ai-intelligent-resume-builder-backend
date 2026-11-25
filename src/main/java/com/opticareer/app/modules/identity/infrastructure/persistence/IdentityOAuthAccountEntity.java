package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    schema = "identity",
    name = "oauth_accounts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "subject"}))
public class IdentityOAuthAccountEntity extends JpaAudit {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID userId;

  @Column(nullable = false, length = 20)
  public String provider;

  @Column(nullable = false, length = 255)
  public String subject;

  public IdentityOAuthAccountEntity() {}
}
