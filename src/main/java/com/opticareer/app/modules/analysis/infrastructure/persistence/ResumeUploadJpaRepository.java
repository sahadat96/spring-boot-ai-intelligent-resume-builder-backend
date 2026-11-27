package com.opticareer.app.modules.analysis.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeUploadJpaRepository extends JpaRepository<ResumeUploadEntity, UUID> {
    Optional<ResumeUploadEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<ResumeUploadEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    void deleteByIdAndOwnerId(UUID id, UUID ownerId);
    void deleteByOwnerId(UUID ownerId);
}
