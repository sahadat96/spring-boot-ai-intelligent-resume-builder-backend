package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "PlatformDeletionAudit")
@Table(name = "deletion_audit", schema = "platform")
public class DeletionAuditEntity extends JpaAudit {
  @Id public UUID id;

  @Column(name = "requested_at", nullable = false)
  public Instant requestedAt;

  @Column(name = "completed_at")
  public Instant completedAt;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(name = "error_code", length = 64)
  public String error;

  public DeletionAuditEntity() {}
}
