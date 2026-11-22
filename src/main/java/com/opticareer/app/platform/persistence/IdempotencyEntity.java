package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity(name = "PlatformIdempotency")
@Table(name = "idempotency_keys", schema = "platform")
public class IdempotencyEntity extends JpaAudit {
  @EmbeddedId public IdempotencyId id;

  @Column(name = "request_hash", nullable = false, length = 64)
  public String requestHash;

  @Column(name = "response_enc", nullable = false, columnDefinition = "text")
  public String responseEnc;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  public IdempotencyEntity() {}
}
