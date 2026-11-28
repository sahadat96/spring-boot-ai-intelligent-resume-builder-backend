package com.opticareer.app.modules.account.application;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.account.infrastructure.persistence.AccountProfileEntity;
import com.opticareer.app.modules.account.infrastructure.persistence.AccountProfileJpaRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements AccountApi {

  private static final int MAX_FULL_NAME_LENGTH = 150;
  private static final int MAX_PART_NAME_LENGTH = 100;

  private final AccountProfileJpaRepository profiles;

  public AccountService(AccountProfileJpaRepository profiles) {
    this.profiles = profiles;
  }

  @Override
  @Transactional
  public void initializeProfile(UUID owner, String fullName) {
    initializeProfile(owner, fullName, null, null);
  }

  @Override
  @Transactional
  public void initializeProfile(UUID owner, String fullName, String firstName, String lastName) {
    AccountProfileEntity entity = profiles.findById(owner).orElseGet(AccountProfileEntity::new);
    NameParts parts = resolveNameParts(fullName, firstName, lastName);
    entity.userId = owner;
    entity.fullName = parts.fullName();
    entity.firstName = parts.firstName();
    entity.lastName = parts.lastName();
    profiles.save(entity);
  }

  @Override
  @Transactional
  public void syncOAuthProfile(
      UUID owner, String fullName, String firstName, String lastName, String emailLocalPart) {
    AccountProfileEntity entity = profiles.findById(owner).orElseGet(AccountProfileEntity::new);
    boolean isNew = entity.userId == null;
    if (isNew) {
      entity.userId = owner;
    }

    NameParts incoming = resolveNameParts(fullName, firstName, lastName);
    String fallback = normalizeSpace(emailLocalPart);
    if (incoming.fullName().isBlank() && isNew && fallback != null) {
      incoming = resolveNameParts(fallback, null, null);
    }

    boolean changed = isNew;
    boolean replaceFullName =
        !incoming.fullName().isBlank()
            && (isBlank(entity.fullName) || isPlaceholderName(entity.fullName, fallback));

    if (replaceFullName) {
      entity.fullName = incoming.fullName();
      changed = true;
    }

    // Backfill first/last from Google claims when missing or replacing an email-prefix placeholder
    NameParts derivedFromCurrentFull = resolveNameParts(entity.fullName, null, null);
    if (isBlank(entity.firstName)
        || (replaceFullName && isPlaceholderName(entity.firstName, fallback))) {
      String candidateFirst =
          !incoming.firstName().isBlank() ? incoming.firstName() : derivedFromCurrentFull.firstName();
      if (!candidateFirst.isBlank() && !candidateFirst.equals(entity.firstName)) {
        entity.firstName = candidateFirst;
        changed = true;
      }
    }

    if (isBlank(entity.lastName)
        || (replaceFullName && isPlaceholderName(entity.lastName, fallback))) {
      String candidateLast =
          !incoming.lastName().isBlank() ? incoming.lastName() : derivedFromCurrentFull.lastName();
      if (!candidateLast.isBlank() && !candidateLast.equals(entity.lastName)) {
        entity.lastName = candidateLast;
        changed = true;
      }
    }

    if (changed) {
      profiles.save(entity);
    }
  }

  private static NameParts resolveNameParts(String fullName, String firstName, String lastName) {
    String cleanFull = normalizeSpace(fullName);
    String cleanFirst = normalizeSpace(firstName);
    String cleanLast = normalizeSpace(lastName);

    if (cleanFull == null && (cleanFirst != null || cleanLast != null)) {
      if (cleanFirst != null && cleanLast != null) {
        cleanFull = cleanFirst + " " + cleanLast;
      } else {
        cleanFull = cleanFirst != null ? cleanFirst : cleanLast;
      }
    }

    if (cleanFull != null && cleanFirst == null) {
      String[] tokens = cleanFull.split("\\s+");
      cleanFirst = tokens[0];
      if (cleanLast == null && tokens.length > 1) {
        cleanLast = String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));
      }
    }

    return new NameParts(
        truncate(cleanFull, MAX_FULL_NAME_LENGTH),
        truncate(cleanFirst, MAX_PART_NAME_LENGTH),
        truncate(cleanLast, MAX_PART_NAME_LENGTH));
  }

  private static boolean isPlaceholderName(String existingValue, String emailLocalPart) {
    if (isBlank(existingValue)) {
      return true;
    }
    String trimmed = existingValue.strip();
    if ("google.user".equalsIgnoreCase(trimmed) || "google user".equalsIgnoreCase(trimmed)) {
      return true;
    }
    return emailLocalPart != null && trimmed.equalsIgnoreCase(emailLocalPart);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String normalizeSpace(String value) {
    if (value == null) {
      return null;
    }
    String collapsed = value.strip().replaceAll("\\s+", " ");
    return collapsed.isEmpty() ? null : collapsed;
  }

  private static String truncate(String value, int maxLen) {
    if (value == null) {
      return "";
    }
    return value.length() <= maxLen ? value : value.substring(0, maxLen).strip();
  }

  private record NameParts(String fullName, String firstName, String lastName) {}

  @Override
  @Transactional(readOnly = true)
  public Profile profile(UUID owner) {
    return profiles
        .findById(owner)
        .map(
            e ->
                new Profile(
                    e.userId,
                    e.fullName,
                    e.firstName,
                    e.lastName,
                    e.headline,
                    e.about,
                    e.country,
                    e.visaStatusEnc,
                    e.aiConsentAt != null))
        .orElse(new Profile(owner, "", "", "", "", "", "", null, false));
  }
}
