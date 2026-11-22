package com.opticareer.app.platform.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

@Embeddable
public class EventReceiptId implements Serializable {
  @java.io.Serial private static final long serialVersionUID = 1L;

  @Column(name = "event_id")
  public UUID eventId;

  @Column(length = 100)
  public String consumer;

  public EventReceiptId() {}

  public EventReceiptId(UUID eventId, String consumer) {
    this.eventId = eventId;
    this.consumer = consumer;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof EventReceiptId i
        && Objects.equals(eventId, i.eventId)
        && Objects.equals(consumer, i.consumer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId, consumer);
  }
}
