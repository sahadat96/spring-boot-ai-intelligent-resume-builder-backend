package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity(name = "PlatformJob")
@Table(name = "async_jobs", schema = "platform")
public class JobEntity extends JpaAudit {
  @Id public UUID id;

  @Column(name = "owner_id", nullable = false)
  public UUID ownerId;

  @Column(nullable = false, length = 64)
  public String type;

  @Column(name = "payload_enc", nullable = false, columnDefinition = "text")
  public String payloadEnc;

  @Column(nullable = false, length = 16)
  public String status = "QUEUED";

  @Column(nullable = false)
  public int attempts;

  @Column(name = "run_after", nullable = false)
  public Instant runAfter = Instant.now();

  @Column(name = "locked_by")
  public UUID lockedBy;

  @Column(name = "locked_until")
  public Instant lockedUntil;

  @Column(name = "result_enc", columnDefinition = "text")
  public String resultEnc;

  @Column(name = "last_error", length = 64)
  public String lastError;

  @Column(nullable = false)
  public int priority;

  public JobEntity() {}
}
