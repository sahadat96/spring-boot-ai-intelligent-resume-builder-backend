package com.opticareer.app.modules.identity.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "identity", name = "oauth_challenges")
public class IdentityOAuthChallengeEntity {
  @Id
  @Column(length = 64)
  public String tokenHash;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant consumedAt;

  @Column(nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  public IdentityOAuthChallengeEntity() {}
}
