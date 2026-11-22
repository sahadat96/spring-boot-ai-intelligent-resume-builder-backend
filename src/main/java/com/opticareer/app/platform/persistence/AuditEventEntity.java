package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity(name = "PlatformAuditEvent")
@Table(name = "audit_events", schema = "platform")
public class AuditEventEntity {
  @Id public UUID id;

  @Column(name = "owner_id")
  public UUID ownerId;

  @Column(nullable = false, length = 80)
  public String type;

  @Column(name = "trace_id", length = 64)
  public String traceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  public Map<String, Object> details = Map.of();

  public AuditEventEntity() {}
}
