package com.opticareer.app.modules.dashboard.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardSnapshot(
    UserSummary user,
    CareerSnapshot careerSnapshot,
    BiggestOpportunity biggestOpportunity,
    List<String> profileHighlights,
    List<BestFitRole> bestFitRoles,
    List<RecommendedMove> recommendedNextMoves
) {
    public record UserSummary(
        UUID userId,
        String firstName,
        String displayName,
        String fullName,
        String headline,
        String visaStatus,
        String planCode,
        String planLabel
    ) {}

    public record CareerSnapshot(
        boolean hasAnalysis,
        UUID latestAnalysisId,
        UUID latestFileId,
        String latestFileName,
        String careerDirection,
        int matchPercentage,
        String readinessLevel,
        String readinessLabel,
        String analysisStatus,
        String analysisStatusLabel,
        int potentialLiftPoints,
        Integer overallScore,
        Integer atsScore,
        Integer contentScore,
        Integer formattingScore,
        boolean ocrUsed,
        boolean aiEnhanced,
        Instant analyzedAt
    ) {}

    public record BiggestOpportunity(
        String title,
        String description,
        int matchLiftPercentage,
        List<String> missingSkills,
        String topMissingRule,
        String ctaLabel,
        String ctaRoute
    ) {}

    public record BestFitRole(
        String roleTitle,
        String domain,
        int matchPercentage,
        List<String> matchedSkills,
        List<String> missingCoreSkills
    ) {}

    public record RecommendedMove(
        String id,
        String title,
        String subtitle,
        String route
    ) {}
}
