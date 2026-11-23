package com.opticareer.app.platform.jobs;

import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.platform.persistence.*;
import com.opticareer.app.shared.id.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresJobQueue implements JobQueue {
  private final JobJpaRepository repository;
  private final FieldCipher cipher;
  private final Json json;

  public PostgresJobQueue(JobJpaRepository repository, FieldCipher cipher, Json json) {
    this.repository = repository;
    this.cipher = cipher;
    this.json = json;
  }

  @Override
  public UUID enqueue(UUID owner, String type, Object payload) {
    return create(owner, type, payload, Instant.now(), 0);
  }

  @Override
  public UUID enqueuePriority(UUID owner, String type, Object payload, int priority) {
    return create(owner, type, payload, Instant.now(), priority);
  }

  @Override
  public UUID enqueueAt(UUID owner, String type, Object payload, Instant after) {
    return create(owner, type, payload, after, 0);
  }

  private UUID create(UUID owner, String type, Object payload, Instant after, int priority) {
    if (priority < 0 || priority > 10)
      throw new IllegalArgumentException("Job priority must be between 0 and 10.");
    var e = new JobEntity();
    e.id = UuidV7.next();
    e.ownerId = owner;
    e.type = type;
    e.payloadEnc = cipher.encrypt(json.write(payload), "job:" + e.id);
    e.runAfter = after;
    e.priority = priority;
    repository.save(e);
    return e.id;
  }
}
