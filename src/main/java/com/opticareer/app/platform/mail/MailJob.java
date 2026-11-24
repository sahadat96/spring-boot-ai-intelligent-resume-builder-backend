package com.opticareer.app.platform.mail;

import java.time.Instant;
import java.util.UUID;

public record MailJob(
    UUID id,
    String email,
    String subject,
    String text,
    String html,
    Instant createdAt,
    int attempts
) {
  public MailJob(String email, String subject, String text) {
    this(UUID.randomUUID(), email, subject, text, null, Instant.now(), 0);
  }

  public MailJob(String email, String subject, String text, String html) {
    this(UUID.randomUUID(), email, subject, text, html, Instant.now(), 0);
  }

  public MailJob withIncrementedAttempts() {
    return new MailJob(id, email, subject, text, html, createdAt, attempts + 1);
  }
}
