package com.opticareer.app.modules.account.application;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.account.infrastructure.persistence.AccountProfileEntity;
import com.opticareer.app.modules.account.infrastructure.persistence.AccountProfileJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements AccountApi {

  private final AccountProfileJpaRepository profiles;

  public AccountService(AccountProfileJpaRepository profiles) {
    this.profiles = profiles;
  }

  @Override
  @Transactional
  public void initializeProfile(UUID owner, String fullName) {
    AccountProfileEntity entity = profiles.findById(owner).orElseGet(AccountProfileEntity::new);
    entity.userId = owner;
    entity.fullName = fullName != null ? fullName.strip() : "";
    profiles.save(entity);
  }

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
