package com.opticareer.app.platform.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DeletionAuditJpaRepository extends JpaRepository<DeletionAuditEntity, UUID> {
  long countByStatus(String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PlatformDeletionAudit a where a.id=:id")
  Optional<DeletionAuditEntity> lockById(@Param("id") UUID id);

  @Modifying
  @Query("delete from PlatformDeletionAudit a where a.status='COMPLETE' and a.completedAt<:cutoff")
  void deleteCompletedBefore(@Param("cutoff") Instant cutoff);
}
