package com.opticareer.app.modules.account;

import java.util.UUID;

public interface AccountApi {
  void initializeProfile(UUID owner, String fullName);

  Profile profile(UUID owner);

  record Profile(
      UUID userId,
      String fullName,
      String firstName,
      String lastName,
      String headline,
      String about,
      String country,
      String visaStatus,
      boolean aiConsent) {}
}
