package com.opticareer.app.platform.persistence;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
  long countByPublishedAtIsNullAndDeadAtIsNull();

  long countByDeadAtIsNotNull();

  @Query(
      value =
"""
select e.* from platform.outbox_events e where e.published_at is null and e.dead_at is null
and e.run_after<=now() and (e.locked_until is null or e.locked_until<now())
order by e.created_at,e.id for update skip locked limit 20
""",
      nativeQuery = true)
  List<OutboxEventEntity> claimCandidates();

  @Transactional
  @Query(
      value =
          "select e.* from platform.outbox_events e where e.id=:id and e.locked_by=:token and e.published_at is null and e.dead_at is null and e.locked_until>clock_timestamp() for update",
      nativeQuery = true)
  Optional<OutboxEventEntity> liveClaim(UUID id, UUID token);

  @Modifying
  @Transactional
  @Query(
      "update PlatformOutboxEvent e set e.publishedAt=CURRENT_TIMESTAMP,e.payloadEnc='',e.lockedUntil=null,e.updatedAt=CURRENT_TIMESTAMP,e.version=e.version+1 where e.id=:id and e.lockedBy=:token")
  int published(UUID id, UUID token);

  @Modifying
  @Transactional
  @Query(
      "update PlatformOutboxEvent e set e.runAfter=:after,e.lockedUntil=null,e.deadAt=:dead,e.lastError='EVENT_DELIVERY_FAILED',e.updatedAt=CURRENT_TIMESTAMP,e.version=e.version+1 where e.id=:id and e.lockedBy=:token")
  int failed(UUID id, UUID token, Instant after, Instant dead);
}
