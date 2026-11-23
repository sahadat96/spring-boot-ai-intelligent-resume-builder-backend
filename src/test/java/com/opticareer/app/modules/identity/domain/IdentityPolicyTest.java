package com.opticareer.app.modules.identity.domain;

import static org.assertj.core.api.Assertions.*;

import com.opticareer.app.shared.error.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityPolicyTest {

  @Test
  @DisplayName("Rejects null, too short, and oversized UTF-8 passwords")
  void rejectsInvalidLength() {
    assertThatThrownBy(() -> PasswordPolicy.validate(null))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("at least 12 characters");

    assertThatThrownBy(() -> PasswordPolicy.validate("Short1!"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("at least 12 characters");

    // 25 4-byte emoji chars = 100 bytes > 72 bytes
    assertThatThrownBy(() -> PasswordPolicy.validate("😀".repeat(25)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("at most 72 UTF-8 bytes");
  }

  @Test
  @DisplayName("Rejects predictable passwords and passwords with low character diversity")
  void rejectsPredictablePasswords() {
    assertThatThrownBy(() -> PasswordPolicy.validate("password1234"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("less predictable");

    assertThatThrownBy(() -> PasswordPolicy.validate("password12345"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("less predictable");

    assertThatThrownBy(() -> PasswordPolicy.validate("123456789012"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("less predictable");

    assertThatThrownBy(() -> PasswordPolicy.validate("aaaaaaaaaaaa"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("less predictable");
  }

  @Test
  @DisplayName("Accepts secure production-grade passwords")
  void acceptsSecurePasswords() {
    assertThatCode(() -> PasswordPolicy.validate("CorrectHorseBatteryStaple123!"))
        .doesNotThrowAnyException();

    assertThatCode(() -> PasswordPolicy.validate("A long correct horse battery staple"))
        .doesNotThrowAnyException();
  }
}
