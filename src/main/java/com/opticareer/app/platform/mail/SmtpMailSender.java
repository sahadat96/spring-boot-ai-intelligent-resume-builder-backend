package com.opticareer.app.platform.mail;

import com.opticareer.app.platform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

  private final JavaMailSender mail;
  private final AppProperties properties;

  public SmtpMailSender(JavaMailSender mail, AppProperties properties) {
    this.mail = mail;
    this.properties = properties;
  }

  public void deliver(MailJob job) {
    long start = System.currentTimeMillis();
    try {
      if (job.html() != null && !job.html().isBlank()) {
        deliverHtml(job.email(), job.subject(), job.text(), job.html());
      } else {
        deliverPlain(job.email(), job.subject(), job.text());
      }
      long duration = System.currentTimeMillis() - start;
      log.info("Successfully delivered queued email [{}] to {} via SMTP in {}ms", job.id(), job.email(), duration);
    } catch (Exception e) {
      log.warn("Failed to deliver email [{}] to {}: {}", job.id(), job.email(), e.getMessage());
      throw new IllegalStateException("Failed to send email notification: " + e.getMessage(), e);
    }
  }

  private void deliverPlain(String email, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.mailFrom());
    message.setTo(email);
    message.setSubject(subject);
    message.setText(text);
    mail.send(message);
  }

  private void deliverHtml(String email, String subject, String plain, String html) throws Exception {
    var message = mail.createMimeMessage();
    var helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setFrom(properties.mailFrom());
    helper.setTo(email);
    helper.setSubject(subject);
    helper.setText(plain, html);
    mail.send(message);
  }
}
