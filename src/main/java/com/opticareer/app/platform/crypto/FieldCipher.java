package com.opticareer.app.platform.crypto;

import com.opticareer.app.platform.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class FieldCipher {
  private final SecretKeySpec key;
  private final String keyId;
  private final java.util.Map<String, SecretKeySpec> keys = new java.util.HashMap<>();
  private final SecureRandom random = new SecureRandom();

  public FieldCipher(
      AppProperties properties,
      @org.springframework.beans.factory.annotation.Value(
              "${app.security.previous-encryption-keys:}")
          String previous) {
    byte[] raw = Base64.getDecoder().decode(properties.security().encryptionKey());
    if (raw.length != 32)
      throw new IllegalArgumentException("ENCRYPTION_KEY must encode 32 bytes.");
    key = new SecretKeySpec(raw, "AES");
    keyId = properties.security().encryptionKeyId();
    if (!keyId.matches("[a-zA-Z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Invalid encryption key ID.");
    keys.put(keyId, key);
    if (!previous.isBlank())
      for (String entry : previous.split(",")) {
        String[] pair = entry.split(":", 2);
        if (pair.length != 2 || !pair[0].matches("[a-zA-Z0-9_-]{1,32}"))
          throw new IllegalArgumentException("Invalid previous encryption key.");
        byte[] bytes = Base64.getDecoder().decode(pair[1]);
        if (bytes.length != 32 || keys.containsKey(pair[0]))
          throw new IllegalArgumentException("Invalid previous encryption key.");
        keys.put(pair[0], new SecretKeySpec(bytes, "AES"));
      }
  }

  public String encrypt(String value, String context) {
    if (value == null) return null;
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return keyId
          + "."
          + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
          + "."
          + Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Field encryption failed.", e);
    }
  }

  public String decrypt(String value, String context) {
    if (value == null) return null;
    try {
      String[] parts = value.split("\\.", 3);
      if (parts.length != 3 || !keys.containsKey(parts[0]))
        throw new IllegalArgumentException("Unknown encryption key.");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          keys.get(parts[0]),
          new GCMParameterSpec(128, Base64.getUrlDecoder().decode(parts[1])));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));   
      return new String(
          cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Field decryption failed.", e);
    }
  }
}
