package com.opticareer.app.modules.account.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProfileJpaRepository
    extends JpaRepository<AccountProfileEntity, UUID> {}
