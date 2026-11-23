package com.opticareer.app.modules.identity.infrastructure.persistence;

import com.opticareer.app.modules.identity.domain.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentitySessionJpaRepository extends JpaRepository<IdentitySessionEntity, UUID> {
  @Query(
      "select new com.opticareer.app.modules.identity.domain.Session(s.id, s.userId, s.familyId, s.expiresAt, s.rotatedAt) from IdentitySessionEntity s where s.tokenHash = :hash")
  Optional<Session> token(@Param("hash") String hash);
}
