package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "users")
public class IdentityUserEntity extends JpaAudit {
  @Id public UUID id;

  @Column(nullable = false, length = 254, unique = true)
  public String email;

  public Instant emailVerifiedAt;

  @Column(nullable = false, length = 24)
  public String status = "ACTIVE";

  @Column(nullable = false, length = 16)
  public String role = "USER";

  public IdentityUserEntity() {}
}
