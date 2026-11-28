package com.opticareer.app.modules.account;

import java.util.UUID;

public interface AccountApi {
  void initializeProfile(UUID owner, String fullName);

  default void initializeProfile(
      UUID owner, String fullName, String firstName, String lastName) {
    initializeProfile(owner, fullName);
  }

  default void syncOAuthProfile(
      UUID owner, String fullName, String firstName, String lastName, String emailLocalPart) {}

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
