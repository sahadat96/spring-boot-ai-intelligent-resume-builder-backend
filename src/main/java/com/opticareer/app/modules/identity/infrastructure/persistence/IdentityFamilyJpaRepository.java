package com.opticareer.app.modules.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IdentityFamilyJpaRepository extends JpaRepository<IdentityFamilyEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from IdentityFamilyEntity f where f.id = :id")
  Optional<IdentityFamilyEntity> lock(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select f from IdentityFamilyEntity f where f.userId = :owner and f.revokedAt is null order by f.id")
  List<IdentityFamilyEntity> activeLocked(@Param("owner") UUID owner);

  Optional<IdentityFamilyEntity> findByIdAndUserId(UUID id, UUID owner);

  List<IdentityFamilyEntity> findByUserIdOrderByCreatedAtDesc(UUID owner, Pageable page);

  @Query(
      "select count(f) from IdentityFamilyEntity f where f.id = :id and f.userId = :owner and f.revokedAt is null and f.expiresAt > :now and exists (select u.id from IdentityUserEntity u where u.id = f.userId and u.status = 'ACTIVE')")
  long activeCount(@Param("id") UUID id, @Param("owner") UUID owner, @Param("now") Instant now);
}
