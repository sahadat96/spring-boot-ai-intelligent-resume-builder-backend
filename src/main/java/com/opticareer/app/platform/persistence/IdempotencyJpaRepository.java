package com.opticareer.app.platform.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IdempotencyJpaRepository extends JpaRepository<IdempotencyEntity, IdempotencyId> {
  @Modifying
  @Query("delete from PlatformIdempotency i where i.id.ownerId=:owner")
  void deleteOwned(@Param("owner") UUID owner);

  @Modifying
  @Query("delete from PlatformIdempotency i where i.expiresAt<:cutoff")
  void deleteExpired(@Param("cutoff") Instant cutoff);
}
