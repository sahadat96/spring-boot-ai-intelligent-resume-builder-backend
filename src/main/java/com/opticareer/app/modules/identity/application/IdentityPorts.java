package com.opticareer.app.modules.identity.application;

import java.util.UUID;

public final class IdentityPorts {
  private IdentityPorts() {}

  public interface LoginLimits {
    void attempt(String email);

    void verification(String email);
  }

  public interface Passwords {
    String hash(String password);

    boolean matches(String password, String hash);

    void checkBreached(String password);
  }

  public interface Tokens {
    String access(UUID owner, UUID family);
  }

  public interface Google {
    Verified verify(String token, String nonce);

    record Verified(String subject, String email, String fullName) {
      public Verified(String subject, String email) {
        this(subject, email, null);
      }
    }
  }
}
