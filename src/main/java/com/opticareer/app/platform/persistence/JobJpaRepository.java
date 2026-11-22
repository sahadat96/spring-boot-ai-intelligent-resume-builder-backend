package com.opticareer.app.platform.persistence;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

public interface JobJpaRepository extends JpaRepository<JobEntity, UUID> {
  Optional<JobEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  long countByStatus(String status);

  @Query(
      value =
"""
select j.* from platform.async_jobs j
where ((j.status='QUEUED' and j.run_after<=now()) or (j.status='RUNNING' and j.locked_until<now()))
and (j.type in ('PRIVACY_DELETE','PRIVACY_STOP_RENEWAL') or not exists(select 1 from platform.blocked_owners b where b.owner_id=j.owner_id))
order by j.priority desc,j.run_after,j.id for update skip locked limit 1
""",
      nativeQuery = true)
  Optional<JobEntity> claimCandidate();

  @Transactional
  @Query(
      value =
          "select j.* from platform.async_jobs j where j.id=:id and j.locked_by=:token and j.status='RUNNING' and j.locked_until>clock_timestamp() for update",
      nativeQuery = true)
  Optional<JobEntity> liveClaim(UUID id, UUID token);

  @Modifying
  @Transactional
  @Query(
      "update PlatformJob j set j.lockedUntil=:until where j.id=:id and j.lockedBy=:token and j.status='RUNNING' and j.lockedUntil>clock_timestamp()")
  int heartbeat(UUID id, UUID token, Instant until);

  @Modifying
  @Transactional
  @Query(
      "update PlatformJob j set j.status='SUCCEEDED',j.resultEnc=:result,j.payloadEnc='',j.lockedUntil=null,j.updatedAt=CURRENT_TIMESTAMP,j.version=j.version+1 where j.id=:id and j.lockedBy=:token and j.status='RUNNING'")
  int succeed(UUID id, UUID token, String result);

  @Modifying
  @Transactional
  @Query(
      "update PlatformJob j set j.status=:status,j.lastError=:error,j.runAfter=:after,j.lockedUntil=null,j.lockedBy=null,j.updatedAt=CURRENT_TIMESTAMP,j.version=j.version+1 where j.id=:id and j.lockedBy=:token and j.status='RUNNING'")
  int fail(UUID id, UUID token, String status, String error, Instant after);
}
