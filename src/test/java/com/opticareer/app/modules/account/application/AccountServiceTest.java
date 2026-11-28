package com.opticareer.app.modules.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.account.infrastructure.persistence.AccountProfileEntity;
import com.opticareer.app.modules.account.infrastructure.persistence.AccountProfileJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountServiceTest {

  private AccountProfileJpaRepository profiles;
  private AccountService service;

  @BeforeEach
  void setUp() {
    profiles = mock(AccountProfileJpaRepository.class);
    service = new AccountService(profiles);
  }

  @Test
  @DisplayName("initializeProfile saves user profile in account schema with full name")
  void initializeProfileSavesFullName() {
    UUID owner = UUID.randomUUID();
    when(profiles.findById(owner)).thenReturn(Optional.empty());

    service.initializeProfile(owner, "  Emran Hossain  ");

    ArgumentCaptor<AccountProfileEntity> captor = ArgumentCaptor.forClass(AccountProfileEntity.class);
    verify(profiles).save(captor.capture());
    AccountProfileEntity saved = captor.getValue();
    assertThat(saved.userId).isEqualTo(owner);
    assertThat(saved.fullName).isEqualTo("Emran Hossain");
    assertThat(saved.firstName).isEqualTo("Emran");
    assertThat(saved.lastName).isEqualTo("Hossain");
  }

  @Test
  @DisplayName("initializeProfile preserves explicit Google given_name and family_name")
  void initializeProfilePreservesGivenAndFamilyNames() {
    UUID owner = UUID.randomUUID();
    when(profiles.findById(owner)).thenReturn(Optional.empty());

    service.initializeProfile(owner, "Mary Jane Watson", "Mary Jane", "Watson");

    ArgumentCaptor<AccountProfileEntity> captor = ArgumentCaptor.forClass(AccountProfileEntity.class);
    verify(profiles).save(captor.capture());
    AccountProfileEntity saved = captor.getValue();
    assertThat(saved.fullName).isEqualTo("Mary Jane Watson");
    assertThat(saved.firstName).isEqualTo("Mary Jane");
    assertThat(saved.lastName).isEqualTo("Watson");
  }

  @Test
  @DisplayName("syncOAuthProfile replaces placeholder or blank names with Google name claims")
  void syncOAuthProfileReplacesPlaceholderWithGoogleName() {
    UUID owner = UUID.randomUUID();
    AccountProfileEntity existing = new AccountProfileEntity(owner, "google.user");
    existing.firstName = "";
    existing.lastName = "";
    when(profiles.findById(owner)).thenReturn(Optional.of(existing));

    service.syncOAuthProfile(owner, "Sahadat Hossain", "Sahadat", "Hossain", "sahadat");

    ArgumentCaptor<AccountProfileEntity> captor = ArgumentCaptor.forClass(AccountProfileEntity.class);
    verify(profiles).save(captor.capture());
    AccountProfileEntity saved = captor.getValue();
    assertThat(saved.fullName).isEqualTo("Sahadat Hossain");
    assertThat(saved.firstName).isEqualTo("Sahadat");
    assertThat(saved.lastName).isEqualTo("Hossain");
  }

  @Test
  @DisplayName("syncOAuthProfile does not overwrite an existing custom fullName")
  void syncOAuthProfilePreservesCustomNameWhileFillingMissingParts() {
    UUID owner = UUID.randomUUID();
    AccountProfileEntity existing = new AccountProfileEntity(owner, "Custom User Name");
    existing.firstName = "Custom";
    existing.lastName = "Name";
    when(profiles.findById(owner)).thenReturn(Optional.of(existing));

    service.syncOAuthProfile(owner, "Sahadat Hossain", "Sahadat", "Hossain", "sahadat");

    verify(profiles, never()).save(any());
  }

  @Test
  @DisplayName("profile returns profile details with full name")
  void profileReturnsDetails() {
    UUID owner = UUID.randomUUID();
    AccountProfileEntity entity = new AccountProfileEntity(owner, "Emran Hossain");
    entity.headline = "Software Engineer";
    when(profiles.findById(owner)).thenReturn(Optional.of(entity));

    AccountApi.Profile result = service.profile(owner);

    assertThat(result.userId()).isEqualTo(owner);
    assertThat(result.fullName()).isEqualTo("Emran Hossain");
    assertThat(result.headline()).isEqualTo("Software Engineer");
  }
}
