package com.opticareer.app.modules.analysis.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ResumeAnalysisJpaRepository extends JpaRepository<ResumeAnalysisEntity, UUID> {
    List<ResumeAnalysisEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<ResumeAnalysisEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<ResumeAnalysisEntity> findFirstByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, String status);
    Optional<ResumeAnalysisEntity> findFirstByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    long countByOwnerIdAndStatus(UUID ownerId, String status);
    void deleteByOwnerId(UUID ownerId);

    @Modifying
    @Query("""
        update ResumeAnalysisEntity a
        set a.status = 'FAILED',
            a.errorMessage = :message,
            a.updatedAt = :now
        where a.status in ('QUEUED', 'PROCESSING')
          and a.updatedAt < :cutoff
        """)
    int failStuckAnalyses(Instant cutoff, Instant now, String message);
}
