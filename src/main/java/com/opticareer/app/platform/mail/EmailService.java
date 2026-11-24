package com.opticareer.app.platform.mail;

/**
 * High-level application service for dispatching domain emails across the platform.
 * Decouples domain modules (Identity, Billing, Account, etc.) from email formatting and transport.
 */
public interface EmailService {

  /**
   * Dispatches a 6-digit email verification OTP to a newly registered or unverified account.
   */
  void sendVerificationOtp(String email, String otp);

  /**
   * Dispatches a 6-digit password reset OTP to an account.
   */
  void sendPasswordResetOtp(String email, String otp);

  /**
   * Dispatches a generic platform notification email.
   */
  void sendNotification(String email, String subject, String message);
}
