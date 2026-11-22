package com.opticareer.app.modules.identity;

import java.util.UUID;

public interface IdentityApi {
  UserSummary user(UUID id);

  void requireSession(UUID owner, UUID family);

  java.util.List<UUID> activeUsers(UUID cursor, int size);

  /** All account IDs for replaying the external erasure journal after database restoration. */
  java.util.List<UUID> recoveryUsers(UUID cursor, int size);

  boolean isAdmin(UUID owner);

  void block(UUID owner);

  void reauthenticate(UUID owner, String password, String otp);

  record UserSummary(UUID id, String email, boolean verified, String status) {}
}
