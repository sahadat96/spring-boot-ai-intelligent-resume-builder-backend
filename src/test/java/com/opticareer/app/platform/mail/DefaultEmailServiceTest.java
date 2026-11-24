package com.opticareer.app.platform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultEmailServiceTest {

  private MailPort mailPort;
  private DefaultEmailService emailService;

  @BeforeEach
  void setUp() {
    mailPort = mock(MailPort.class);
    emailService = new DefaultEmailService(mailPort);
  }

  @Test
  @DisplayName("sendVerificationOtp dispatches both plaintext and styled HTML to MailPort")
  void sendVerificationOtpDispatchesHtmlAndText() {
    emailService.sendVerificationOtp("candidate@example.com", "482910");

    verify(mailPort).sendHtml(
        eq("candidate@example.com"),
        eq("Verify your OptiCareer email"),
        argThat(plain -> plain.contains("482910") && plain.contains("Your 6-digit verification code to verify your account is:")),
        argThat(html -> html.contains("482910") && html.contains("Verify Your Email") && html.contains("OptiCareer"))
    );
  }

  @Test
  @DisplayName("sendPasswordResetOtp dispatches both plaintext and styled HTML to MailPort")
  void sendPasswordResetOtpDispatchesHtmlAndText() {
    emailService.sendPasswordResetOtp("candidate@example.com", "839201");

    verify(mailPort).sendHtml(
        eq("candidate@example.com"),
        eq("Reset your OptiCareer password"),
        argThat(plain -> plain.contains("839201") && plain.contains("Your 6-digit verification code to reset your password is:")),
        argThat(html -> html.contains("839201") && html.contains("Reset Your Password") && html.contains("OptiCareer"))
    );
  }

  @Test
  @DisplayName("sendNotification dispatches notification email to MailPort")
  void sendNotificationDispatches() {
    emailService.sendNotification("candidate@example.com", "Welcome to OptiCareer", "Your account is active.");

    verify(mailPort).sendHtml(
        eq("candidate@example.com"),
        eq("Welcome to OptiCareer"),
        argThat(plain -> plain.contains("Your account is active.")),
        argThat(html -> html.contains("Welcome to OptiCareer") && html.contains("Your account is active."))
    );
  }
}
