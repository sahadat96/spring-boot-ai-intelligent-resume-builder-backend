package com.opticareer.app.modules.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.opticareer.app.platform.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordAdapterTest {

  @Test
  @DisplayName("BCrypt hashing, matching and null handling works as expected")
  void bcryptHashingAndMatching() {
    var adapter = new PasswordAdapter(null);
    String raw = "StrongPassword123!";
    String hash = adapter.hash(raw);

    assertThat(hash).isNotBlank();
    assertThat(adapter.matches(raw, hash)).isTrue();
    assertThat(adapter.matches("WrongPassword123!", hash)).isFalse();
    assertThat(adapter.matches(null, hash)).isFalse();
    assertThat(adapter.matches(raw, null)).isFalse();
  }

  @Test
  @DisplayName("UTF-8 passwords cannot match through bcrypt 72-byte truncation")
  void utf8PasswordsCannotMatchThroughBcryptTruncation() {
    var adapter = new PasswordAdapter(null);
    String secret = "é".repeat(30) + "A".repeat(12);
    String encoded = adapter.hash(secret);

    assertThat(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
    assertThat(adapter.matches(secret, encoded)).isTrue();
    assertThat(adapter.matches(secret + "extra", encoded)).isFalse();
  }

  @Test
  @DisplayName("Breached password check is safely bypassed when disabled in properties")
  void breachCheckBypassedWhenDisabled() {
    AppProperties.Security sec = new AppProperties.Security(
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "v1",
        false,
        "opticareer",
        null,
        10,
        14,
        false
    );
    AppProperties props = new AppProperties("http://localhost:5173", "http://localhost:5173", sec, null, null, null, null, "no-reply@opticareer.com");
    var adapter = new PasswordAdapter(props);

    assertThatCode(() -> adapter.checkBreached("AnyPassword123!"))
        .doesNotThrowAnyException();
  }
}
