
package com.opticareer.app.modules.identity.domain;

import com.opticareer.app.shared.error.DomainException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PasswordPolicy { 
  private PasswordPolicy() {}

  public static void validate(String password) {
    if (password == null
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw DomainException.invalid(
          "Use a password of at least 12 characters and at most 72 UTF-8 bytes.");
    String normalized = password.toLowerCase(Locale.ROOT);
    if (Set.of("password1234", "password12345", "123456789012", "qwerty123456").contains(normalized)
        || normalized.chars().distinct().count() < 4)
      throw DomainException.invalid("Choose a less predictable password.");
  }
}
