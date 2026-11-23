package com.opticareer.app.modules.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IdentityUserJpaRepository extends JpaRepository<IdentityUserEntity, UUID> {
  Optional<IdentityUserEntity> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from IdentityUserEntity u where u.id = :id")
  Optional<IdentityUserEntity> lock(@Param("id") UUID id);

  boolean existsByIdAndRoleAndStatus(UUID id, String role, String status);

  @Query(
      "select u.id from IdentityUserEntity u where u.status = 'ACTIVE' and u.emailVerifiedAt is not null order by u.id")
  List<UUID> active(Pageable page);

  @Query(
      "select u.id from IdentityUserEntity u where u.status = 'ACTIVE' and u.emailVerifiedAt is not null and u.id > :cursor order by u.id")
  List<UUID> activeAfter(@Param("cursor") UUID cursor, Pageable page);

  @Query("select u.id from IdentityUserEntity u order by u.id")
  List<UUID> recovery(Pageable page);

  @Query("select u.id from IdentityUserEntity u where u.id > :cursor order by u.id")
  List<UUID> recoveryAfter(@Param("cursor") UUID cursor, Pageable page);
}
    