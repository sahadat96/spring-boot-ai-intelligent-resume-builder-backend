package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity(name = "PlatformOutboxEvent")
@Table(name = "outbox_events", schema = "platform")
public class OutboxEventEntity extends JpaAudit {
  @Id public UUID id;

  @Column(name = "owner_id", nullable = false)
  public UUID ownerId;

  @Column(nullable = false, length = 80)
  public String type;

  @Column(name = "payload_enc", nullable = false, columnDefinition = "text")
  public String payloadEnc;

  @Column(name = "published_at")
  public Instant publishedAt;

  @Column(nullable = false)
  public int attempts;

  @Column(name = "run_after", nullable = false)
  public Instant runAfter = Instant.now();

  @Column(name = "locked_by")
  public UUID lockedBy;

  @Column(name = "locked_until")
  public Instant lockedUntil;

  @Column(name = "dead_at")
  public Instant deadAt;

  @Column(name = "last_error", length = 64)
  public String lastError;

  public OutboxEventEntity() {}
}
