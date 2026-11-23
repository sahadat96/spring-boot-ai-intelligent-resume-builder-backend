package com.opticareer.app.platform.mail;

public interface MailPort {
  void send(String email, String subject, String text);

  default void sendHtml(String email, String subject, String plain, String html) {
    send(email, subject, plain);
  }
}
