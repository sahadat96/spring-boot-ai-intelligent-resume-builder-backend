package com.opticareer.app.platform.outbox;

import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.jobs.JobLease;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.platform.persistence.*;
import com.opticareer.app.shared.event.EventPublisher;
import com.opticareer.app.shared.id.UuidV7;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresEventPublisher implements EventPublisher {
  private final JobLease lease;
  private final OutboxEventJpaRepository events;
  private final AuditEventJpaRepository audits;
  private final FieldCipher cipher;
  private final Json json;

  public PostgresEventPublisher(
      JobLease lease,
      OutboxEventJpaRepository events,
      AuditEventJpaRepository audits,
      FieldCipher cipher,
      Json json) {
    this.lease = lease;
    this.events = events;
    this.audits = audits;
    this.cipher = cipher;
    this.json = json;
  }

  @Override
  public UUID publish(UUID owner, String type, Map<String, Object> payload) {
    lease.check();
    var e = new OutboxEventEntity();
    e.id = UuidV7.next();
    e.ownerId = owner;
    e.type = type;
    e.payloadEnc = cipher.encrypt(json.write(payload), "event:" + e.id);
    events.save(e);
    var audit = new AuditEventEntity();
    audit.id = UuidV7.next();
    audit.ownerId = owner;
    audit.type = type;
    audit.traceId = org.slf4j.MDC.get("traceId");
    audits.save(audit);
    return e.id;
  }
}
