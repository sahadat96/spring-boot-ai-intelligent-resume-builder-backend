package com.opticareer.app.platform.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link EmailService}.
 * Delegates asynchronous email dispatch to {@link MailPort} (RedisMailQueue).
 */
@Service
public class DefaultEmailService implements EmailService {

  private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);

  private final MailPort mailPort;

  public DefaultEmailService(MailPort mailPort) {
    this.mailPort = mailPort;
  }

  @Override
  public void sendVerificationOtp(String email, String otp) {
    String subject = "Verify your OptiCareer email";
    String plainText = EmailTemplates.verificationOtpPlainText(otp);
    String html = EmailTemplates.verificationOtpHtml(otp);
    mailPort.sendHtml(email, subject, plainText, html);
    log.info("Queued verification OTP email for {}", email);
  }

  @Override
  public void sendPasswordResetOtp(String email, String otp) {
    String subject = "Reset your OptiCareer password";
    String plainText = EmailTemplates.passwordResetOtpPlainText(otp);
    String html = EmailTemplates.passwordResetOtpHtml(otp);
    mailPort.sendHtml(email, subject, plainText, html);
    log.info("Queued password reset OTP email for {}", email);
  }

  @Override
  public void sendNotification(String email, String subject, String message) {
    String plainText = EmailTemplates.notificationPlainText(message);
    String html = EmailTemplates.notificationHtml(subject, message);
    mailPort.sendHtml(email, subject, plainText, html);
    log.info("Queued notification email for {} [subject: {}]", email, subject);
  }
}
