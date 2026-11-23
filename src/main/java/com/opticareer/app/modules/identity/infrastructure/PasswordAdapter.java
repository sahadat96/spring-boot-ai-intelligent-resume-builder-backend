package com.opticareer.app.modules.identity.infrastructure;

import com.opticareer.app.modules.identity.application.IdentityPorts;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.http.BoundedBodyHandlers;
import com.opticareer.app.shared.error.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordAdapter implements IdentityPorts.Passwords {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final AppProperties properties;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public PasswordAdapter(AppProperties properties) {
    this.properties = properties;
  }

  @Override
  public String hash(String password) {
    return encoder.encode(password);
  }

  @Override
  public boolean matches(String password, String hash) {
    return password != null
        && hash != null
        && password.getBytes(StandardCharsets.UTF_8).length <= 72
        && encoder.matches(password, hash);
  }

  @Override
  public void checkBreached(String password) {
    if (properties == null || properties.security() == null || !properties.security().breachedPasswordCheck()) {
      return;
    }
    try {
      String hash =
          HexFormat.of()
              .withUpperCase()
              .formatHex(
                  MessageDigest.getInstance("SHA-1")
                      .digest(password.getBytes(StandardCharsets.UTF_8)));
      var request =
          HttpRequest.newBuilder(
                  URI.create("https://api.pwnedpasswords.com/range/" + hash.substring(0, 5)))
              .header("Add-Padding", "true")
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      var response = BoundedBodyHandlers.send(http, request, BoundedBodyHandlers.utf8(1_048_576));
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Breach check unavailable.");
      }
      if (response
          .body()
          .lines()
          .anyMatch(line -> line.startsWith(hash.substring(5) + ":") && !line.endsWith(":0"))) {
        throw DomainException.invalid(
            "This password has appeared in a breach. Choose another password.");
      }
    } catch (DomainException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new DomainException(
          ErrorCode.PROVIDER_UNAVAILABLE, "Password verification is temporarily unavailable.");
    }
  }
}
