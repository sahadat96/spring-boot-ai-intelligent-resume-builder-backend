package com.opticareer.app.platform.mail;

import com.opticareer.app.platform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailAdapter implements MailPort {

  private static final Logger log = LoggerFactory.getLogger(SmtpMailAdapter.class);

  private final JavaMailSender mail;
  private final AppProperties properties;

  public SmtpMailAdapter(JavaMailSender mail, AppProperties properties) {
    this.mail = mail;
    this.properties = properties;
  }

  @Override
  public void send(String email, String subject, String text) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.mailFrom());
      message.setTo(email);
      message.setSubject(subject);
      message.setText(text);
      mail.send(message);
      log.info("Sent email to {} with subject: {}", email, subject);
    } catch (Exception e) {
      log.warn("Failed to deliver email to {}: {}", email, e.getMessage());
      throw new IllegalStateException("Failed to send email notification: " + e.getMessage(), e);
    }
  }

  @Override
  public void sendHtml(String email, String subject, String plain, String html) {
    try {
      var message = mail.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(properties.mailFrom());
      helper.setTo(email);
      helper.setSubject(subject);
      helper.setText(plain, html);
      mail.send(message);
      log.info("Sent HTML email to {} with subject: {}", email, subject);
    } catch (Exception e) {
      log.warn("Failed to deliver HTML email to {}: {}", email, e.getMessage());
      throw new IllegalStateException("Email could not be prepared: " + e.getMessage(), e);
    }
  }
}
