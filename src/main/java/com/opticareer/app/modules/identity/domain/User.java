package com.opticareer.app.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String email, String passwordHash, Instant verifiedAt, String status) {
  public boolean active() {
    return "ACTIVE".equals(status);
  }

  public boolean verified() {
    return verifiedAt != null;
  }
}
