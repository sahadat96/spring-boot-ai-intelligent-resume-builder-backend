package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity(name = "PlatformEventReceipt")
@Table(name = "event_receipts", schema = "platform")
public class EventReceiptEntity {
  @EmbeddedId public EventReceiptId id;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  public EventReceiptEntity() {}
}
