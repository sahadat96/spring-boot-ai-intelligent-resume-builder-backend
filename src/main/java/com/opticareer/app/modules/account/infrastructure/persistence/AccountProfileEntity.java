package com.opticareer.app.modules.account.infrastructure.persistence;

import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "account", name = "user_profiles")
public class AccountProfileEntity extends JpaAudit {
  @Id
  @Column(name = "user_id")
  public UUID userId;

  @Column(name = "full_name", nullable = false, length = 150)
  public String fullName = "";

  @Column(name = "first_name", nullable = false, length = 100)
  public String firstName = "";

  @Column(name = "last_name", nullable = false, length = 100)
  public String lastName = "";

  @Column(name = "headline", nullable = false, length = 200)
  public String headline = "";

  @Column(name = "about", nullable = false, length = 4000)
  public String about = "";

  @Column(name = "country", nullable = false, length = 100)
  public String country = "";

  @Column(name = "visa_status_enc", columnDefinition = "text")
  public String visaStatusEnc;

  @Column(name = "ai_consent_at")
  public Instant aiConsentAt;

  public AccountProfileEntity() {}

  public AccountProfileEntity(UUID userId, String fullName) {
    this.userId = userId;
    this.fullName = fullName != null ? fullName : "";
  }
}
