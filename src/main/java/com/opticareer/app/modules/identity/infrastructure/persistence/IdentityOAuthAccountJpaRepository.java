package com.opticareer.app.modules.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityOAuthAccountJpaRepository
    extends JpaRepository<IdentityOAuthAccountEntity, UUID> {
  Optional<IdentityOAuthAccountEntity> findByProviderAndSubject(String provider, String subject);
}
