package com.opticareer.app.shared.event;

import java.util.Map;
import java.util.UUID;

public interface EventPublisher {
  UUID publish(UUID owner, String type, Map<String, Object> payload);
}
