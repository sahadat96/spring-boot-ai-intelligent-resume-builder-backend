package com.opticareer.app.modules.analysis.application;

import java.util.UUID;

public record AnalysisJob(
    UUID analysisId,
    UUID ownerId,
    UUID fileId,
    String targetRole,
    String jobDescription,
    int attempts
) {
    public AnalysisJob(UUID analysisId, UUID ownerId, UUID fileId, String targetRole, String jobDescription) {
        this(analysisId, ownerId, fileId, targetRole, jobDescription, 0);
    }

    public AnalysisJob withIncrementedAttempts() {
        return new AnalysisJob(analysisId, ownerId, fileId, targetRole, jobDescription, attempts + 1);
    }
}
