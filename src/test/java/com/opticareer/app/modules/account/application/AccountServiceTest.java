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
