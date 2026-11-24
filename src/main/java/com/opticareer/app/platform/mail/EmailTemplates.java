package com.opticareer.app.platform.mail;

/**
 * Reusable, production-grade email template generator for OptiCareer.
 * Produces clean plain-text alternatives and responsive, styled HTML templates.
 */
public final class EmailTemplates {

  private EmailTemplates() {}

  public static String verificationOtpPlainText(String otp) {
    return String.format("""
        Hello,

        Your 6-digit verification code to verify your account is:

        %s

        This code is valid for 15 minutes. For security reasons, never share this code with anyone.

        If you did not request this code, please ignore this email.

        Best regards,
        The OptiCareer Team
        """, otp);
  }

  public static String verificationOtpHtml(String otp) {
    return renderOtpCard(
        "Verify Your Email",
        "Thank you for signing up for OptiCareer. Use the verification code below to confirm your account and get started.",
        otp,
        "This code will expire in 15 minutes. Never share this code with anyone."
    );
  }

  public static String passwordResetOtpPlainText(String otp) {
    return String.format("""
        Hello,

        Your 6-digit verification code to reset your password is:

        %s

        This code is valid for 15 minutes. For security reasons, never share this code with anyone.

        If you did not request this code, please ignore this email.

        Best regards,
        The OptiCareer Team
        """, otp);
  }

  public static String passwordResetOtpHtml(String otp) {
    return renderOtpCard(
        "Reset Your Password",
        "We received a request to reset the password for your OptiCareer account. Use the code below to proceed.",
        otp,
        "This code will expire in 15 minutes. If you did not request a password reset, you can safely ignore this email."
    );
  }

  public static String notificationPlainText(String message) {
    return String.format("""
        Hello,

        %s

        Best regards,
        The OptiCareer Team
        """, message);
  }

  public static String notificationHtml(String title, String message) {
    return String.format("""
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>%s</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
          <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F8FAFC; padding: 40px 20px;">
            <tr>
              <td align="center">
                <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 520px; background-color: #FFFFFF; border-radius: 12px; border: 1px solid #E2E8F0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);">
                  <tr>
                    <td style="padding: 32px 40px; background: linear-gradient(135deg, #1E3A8A 0%%, #2563EB 100%%); text-align: center;">
                      <h1 style="margin: 0; color: #FFFFFF; font-size: 24px; font-weight: 700; letter-spacing: -0.5px;">OptiCareer</h1>
                      <p style="margin: 4px 0 0; color: #BFDBFE; font-size: 13px;">AI-Intelligent Resume & Career Platform</p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding: 36px 40px;">
                      <h2 style="margin: 0 0 16px; color: #0F172A; font-size: 18px; font-weight: 600;">%s</h2>
                      <p style="margin: 0; color: #475569; font-size: 14px; line-height: 24px;">%s</p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding: 20px 40px; background-color: #F8FAFC; border-top: 1px solid #E2E8F0; text-align: center;">
                      <p style="margin: 0; color: #94A3B8; font-size: 12px;">© %d OptiCareer. All rights reserved.</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """, title, title, message, java.time.Year.now().getValue());
  }

  private static String renderOtpCard(String heading, String intro, String otp, String footerNote) {
    return String.format("""
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>%s</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
          <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F8FAFC; padding: 40px 20px;">
            <tr>
              <td align="center">
                <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 520px; background-color: #FFFFFF; border-radius: 12px; border: 1px solid #E2E8F0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);">
                  <!-- Header -->
                  <tr>
                    <td style="padding: 32px 40px; background: linear-gradient(135deg, #0F172A 0%%, #1E293B 100%%); text-align: center;">
                      <h1 style="margin: 0; color: #FFFFFF; font-size: 24px; font-weight: 700; letter-spacing: -0.5px;">OptiCareer</h1>
                      <p style="margin: 4px 0 0; color: #94A3B8; font-size: 13px;">AI-Intelligent Career Platform</p>
                    </td>
                  </tr>
                  <!-- Body -->
                  <tr>
                    <td style="padding: 36px 40px;">
                      <h2 style="margin: 0 0 12px; color: #0F172A; font-size: 20px; font-weight: 600;">%s</h2>
                      <p style="margin: 0 0 24px; color: #475569; font-size: 14px; line-height: 22px;">%s</p>
                      
                      <!-- OTP Box -->
                      <div style="background-color: #F1F5F9; border: 2px dashed #CBD5E1; border-radius: 10px; padding: 20px; text-align: center; margin: 28px 0;">
                        <span style="font-family: 'Courier New', Courier, monospace; font-size: 34px; font-weight: 700; letter-spacing: 8px; color: #0F172A; display: inline-block;">%s</span>
                      </div>
                      
                      <!-- Security Note -->
                      <p style="margin: 0; color: #64748B; font-size: 13px; line-height: 20px;">
                        <strong>Security Notice:</strong> %s
                      </p>
                    </td>
                  </tr>
                  <!-- Footer -->
                  <tr>
                    <td style="padding: 24px 40px; background-color: #F8FAFC; border-top: 1px solid #E2E8F0; text-align: center;">
                      <p style="margin: 0 0 4px; color: #94A3B8; font-size: 12px;">This is an automated message. Please do not reply directly to this email.</p>
                      <p style="margin: 0; color: #94A3B8; font-size: 12px;">© %d OptiCareer Platform. All rights reserved.</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """, heading, heading, intro, otp, footerNote, java.time.Year.now().getValue());
  }
}
