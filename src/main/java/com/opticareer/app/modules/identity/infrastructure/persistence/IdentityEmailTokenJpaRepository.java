package com.opticareer.app.modules.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IdentityEmailTokenJpaRepository
    extends JpaRepository<IdentityEmailTokenEntity, UUID> {
  @Query(
      "select t.userId from IdentityEmailTokenEntity t where t.tokenHash = :hash and t.purpose = :purpose and t.consumedAt is null and t.expiresAt > :now")
  Optional<UUID> tokenOwner(
      @Param("hash") String hash, @Param("purpose") String purpose, @Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from IdentityEmailTokenEntity t where t.userId = :owner and t.purpose = :purpose and t.consumedAt is null order by t.id")
  List<IdentityEmailTokenEntity> pending(
      @Param("owner") UUID owner, @Param("purpose") String purpose);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from IdentityEmailTokenEntity t where t.tokenHash = :hash and t.purpose = :purpose")
  Optional<IdentityEmailTokenEntity> lockToken(
      @Param("hash") String hash, @Param("purpose") String purpose);
}
