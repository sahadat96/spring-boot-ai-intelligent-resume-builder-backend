package com.opticareer.app.modules.identity.domain;

import java.time.Instant;
import java.util.*;

public interface IdentityRepository {
  void lockEmail(String email);

  Optional<User> byEmail(String email);

  void create(User user);

  void password(UUID owner, String hash);

  void token(UUID id, UUID owner, String hash, String purpose, Instant expires);

  record Mfa(String encrypted, boolean confirmed, long lastCounter) {}
}
