package com.opticareer.app.platform.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedOwnerJpaRepository extends JpaRepository<BlockedOwnerEntity, UUID> {}
