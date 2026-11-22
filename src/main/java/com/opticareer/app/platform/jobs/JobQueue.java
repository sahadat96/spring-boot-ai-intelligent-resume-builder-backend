package com.opticareer.app.platform.jobs;

import java.util.UUID;

public interface JobQueue {
  UUID enqueue(UUID owner, String type, Object payload);

  UUID enqueuePriority(UUID owner, String type, Object payload, int priority);

  UUID enqueueAt(UUID owner, String type, Object payload, java.time.Instant after);
}
