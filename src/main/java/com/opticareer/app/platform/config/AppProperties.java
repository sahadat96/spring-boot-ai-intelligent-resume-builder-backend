package com.opticareer.app.platform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app")
@Validated
public record AppProperties(
    @NotBlank String publicUrl,
    @NotBlank String corsOrigins,
    @Valid Security security,
    @Valid Storage storage,
    @Valid Ai ai,
    @Valid Stripe stripe,
    @Valid Worker worker,
    @NotBlank String mailFrom) {
  public record Security(
      @NotBlank String jwtKey,
      @NotBlank String encryptionKey,
      @NotBlank String encryptionKeyId,
      boolean cookieSecure,
      @NotBlank String issuer,
      String googleClientId,
      @Min(1) @Max(15) int accessMinutes,
      @Min(1) int refreshDays,
      boolean breachedPasswordCheck) {
    @Override
    public String toString() {
      return "Security[credentials=REDACTED]";
    }
  }

  public record Storage(
      @NotBlank String endpoint,
      @NotBlank String region,
      @NotBlank String bucket,
      @NotBlank String accessKey,
      @NotBlank String secretKey,
      @Min(1) @Max(15) int signedUrlMinutes,
      @NotBlank String scanHost,
      @Min(1) int scanPort,
      boolean scanRequired) {
    @Override
    public String toString() {
      return "Storage[credentials=REDACTED]";
    }
  }

  public record Ai(
      @NotBlank String url,
      String apiKey,
      @NotBlank String model,
      @Min(1) @Max(120) int timeoutSeconds,
      @Min(1) @Max(32) int maxConcurrent,
      @Min(1) long dailyTokenLimit,
      boolean enabled) {
    @Override
    public String toString() {
      return "Provider[credentials=REDACTED]";
    }
  }

  public record Stripe(
      String apiKey,
      String webhookSecret,
      @NotBlank String baseUrl,
      String proMonthly,
      String proSixMonth,
      String plusMonthly,
      String plusSixMonth,
      boolean enabled) {
    @Override
    public String toString() {
      return "Provider[credentials=REDACTED]";
    }
  }

  public record Worker(
      boolean enabled,
      @Min(100) long pollMs,
      @Min(1) int maxAttempts,
      @Min(120) int leaseSeconds) {}
}
