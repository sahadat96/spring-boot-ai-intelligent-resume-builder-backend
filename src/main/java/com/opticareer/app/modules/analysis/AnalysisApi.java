package com.opticareer.app.modules.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public cross-module API for querying user resume analysis summaries without coupling
 * external modules (e.g., dashboard, tailoring) to persistence entities.
 */
public interface AnalysisApi {

    Optional<CompletedAnalysisSnapshot> latestCompletedAnalysis(UUID ownerId);

    Optional<LatestStatus> latestAnalysisStatus(UUID ownerId);

    long completedAnalysisCount(UUID ownerId);

    record CompletedAnalysisSnapshot(
        UUID analysisId,
        UUID fileId,
        String fileName,
        String targetRole,
        int overallScore,
        int atsScore,
        int contentScore,
        int formattingScore,
        Integer jdMatchScore,
        List<String> strengths,
        List<String> areasToImprove,
        List<String> keywords,
        List<String> missingKeywords,
        Map<String, Object> scoreDetails,
        Map<String, String> sections,
        String extractedText,
        boolean ocrUsed,
        boolean aiEnhanced,
        Instant analyzedAt
    ) {}

    record LatestStatus(
        UUID analysisId,
        UUID fileId,
        String fileName,
        String status,
        Instant updatedAt
    ) {}
}
