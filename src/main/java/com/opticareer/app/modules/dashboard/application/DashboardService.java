package com.opticareer.app.modules.dashboard.application;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.analysis.AnalysisApi;
import com.opticareer.app.modules.analysis.AnalysisApi.CompletedAnalysisSnapshot;
import com.opticareer.app.modules.analysis.AnalysisApi.LatestStatus;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BestFitRole;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BiggestOpportunity;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.CareerSnapshot;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.RecommendedMove;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.UserSummary;
import com.opticareer.app.modules.dashboard.domain.RoleMatchingEngine;
import com.opticareer.app.modules.identity.IdentityApi;
import com.opticareer.app.platform.crypto.FieldCipher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final AccountApi accountApi;
    private final IdentityApi identityApi;
    private final AnalysisApi analysisApi;
    private final PlanInfoPort planInfoPort;
    private final RoleMatchingEngine roleEngine;
    private final @Nullable FieldCipher cipher;

    public DashboardService(
            AccountApi accountApi,
            IdentityApi identityApi,
            AnalysisApi analysisApi,
            PlanInfoPort planInfoPort,
            RoleMatchingEngine roleEngine,
            @Nullable FieldCipher cipher) {
        this.accountApi = accountApi;
        this.identityApi = identityApi;
        this.analysisApi = analysisApi;
        this.planInfoPort = planInfoPort;
        this.roleEngine = roleEngine;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot getDashboard(UUID ownerId, int topRolesLimit) {
        AccountApi.Profile profile = accountApi.profile(ownerId);
        IdentityApi.UserSummary identity = safeIdentity(ownerId);
        PlanInfoPort.PlanInfo plan = planInfoPort.currentPlan(ownerId);

        UserSummary userSummary = buildUserSummary(ownerId, profile, identity, plan);

        Optional<CompletedAnalysisSnapshot> completedOpt = analysisApi.latestCompletedAnalysis(ownerId);
        Optional<LatestStatus> latestStatusOpt = analysisApi.latestAnalysisStatus(ownerId);

        if (completedOpt.isEmpty()) {
            return buildEmptyDashboard(userSummary, latestStatusOpt.orElse(null), profile.headline());
        }

        CompletedAnalysisSnapshot completed = completedOpt.get();
        List<BestFitRole> bestFitRoles = roleEngine.rankRoles(completed, profile.headline(), topRolesLimit);
        String careerDirection = roleEngine.resolveCareerDirection(completed, profile.headline(), bestFitRoles);
        int matchPct = roleEngine.calculateSnapshotMatch(completed, careerDirection, bestFitRoles);

        List<String> topMissingSkills = bestFitRoles.stream()
            .filter(r -> r.roleTitle().equalsIgnoreCase(careerDirection))
            .findFirst()
            .or(() -> bestFitRoles.stream().findFirst())
            .map(BestFitRole::missingCoreSkills)
            .orElse(completed.missingKeywords());

        int potentialLift = roleEngine.calculatePotentialLift(completed, matchPct, topMissingSkills);
        BiggestOpportunity opportunity = roleEngine.buildBiggestOpportunity(
            completed, careerDirection, matchPct, potentialLift, bestFitRoles
        );
        List<String> highlights = roleEngine.buildProfileHighlights(completed, bestFitRoles);
        List<RecommendedMove> moves = roleEngine.buildRecommendedMoves(completed, careerDirection, bestFitRoles, opportunity);

        String readinessLevel = matchPct >= 80 ? "HIGH" : (matchPct >= 55 ? "MODERATE" : "EARLY");
        String readinessLabel = switch (readinessLevel) {
            case "HIGH" -> "High readiness";
            case "MODERATE" -> "Moderate readiness";
            default -> "Needs optimization";
        };

        String latestStatus = latestStatusOpt.map(LatestStatus::status).orElse("COMPLETED");
        String statusLabel = ("QUEUED".equals(latestStatus) || "PROCESSING".equals(latestStatus))
            ? "New analysis in progress"
            : "Analysis complete";

        CareerSnapshot snapshot = new CareerSnapshot(
            true,
            completed.analysisId(),
            completed.fileId(),
            completed.fileName(),
            careerDirection,
            matchPct,
            readinessLevel,
            readinessLabel,
            latestStatus,
            statusLabel,
            potentialLift,
            completed.overallScore(),
            completed.atsScore(),
            completed.contentScore(),
            completed.formattingScore(),
            completed.ocrUsed(),
            completed.aiEnhanced(),
            completed.analyzedAt()
        );

        return new DashboardSnapshot(userSummary, snapshot, opportunity, highlights, bestFitRoles, moves);
    }

    @Transactional(readOnly = true)
    public List<BestFitRole> getCareerTargets(UUID ownerId, int limit) {
        AccountApi.Profile profile = accountApi.profile(ownerId);
        return analysisApi.latestCompletedAnalysis(ownerId)
            .map(completed -> roleEngine.rankRoles(completed, profile.headline(), limit))
            .orElse(List.of());
    }

    private DashboardSnapshot buildEmptyDashboard(
            UserSummary userSummary,
            LatestStatus latestStatus,
            String headline) {
        String status = latestStatus != null ? latestStatus.status() : "NONE";
        String statusLabel = switch (status) {
            case "QUEUED", "PROCESSING" -> "Analysis in progress";
            case "FAILED" -> "Last analysis failed — retry upload";
            default -> "No analysis yet";
        };
        String direction = (headline != null && !headline.isBlank()) ? headline.trim() : "Target Role Not Set";

        CareerSnapshot snapshot = new CareerSnapshot(
            false,
            latestStatus != null ? latestStatus.analysisId() : null,
            latestStatus != null ? latestStatus.fileId() : null,
            latestStatus != null ? latestStatus.fileName() : null,
            direction,
            0,
            "NONE",
            "Upload resume to measure readiness",
            status,
            statusLabel,
            0,
            null,
            null,
            null,
            null,
            false,
            false,
            null
        );

        BiggestOpportunity opportunity = roleEngine.buildBiggestOpportunity(null, direction, 0, 0, List.of());
        List<String> highlights = roleEngine.buildProfileHighlights(null, List.of());
        List<RecommendedMove> moves = roleEngine.buildRecommendedMoves(null, direction, List.of(), opportunity);

        return new DashboardSnapshot(userSummary, snapshot, opportunity, highlights, List.of(), moves);
    }

    private UserSummary buildUserSummary(
            UUID ownerId,
            AccountApi.Profile profile,
            IdentityApi.UserSummary identity,
            PlanInfoPort.PlanInfo plan) {
        String fullName = profile.fullName() != null && !profile.fullName().isBlank()
            ? profile.fullName().trim()
            : fallbackNameFromEmail(identity != null ? identity.email() : null);

        String firstName = (profile.firstName() != null && !profile.firstName().isBlank())
            ? profile.firstName().trim()
            : extractFirstName(fullName);

        String displayName = formatCompactDisplayName(fullName, firstName, profile.lastName());
        String cleanVisa = resolveSafeVisaStatus(ownerId, profile.visaStatus());

        return new UserSummary(
            ownerId,
            firstName,
            displayName,
            fullName,
            profile.headline() != null ? profile.headline() : "",
            cleanVisa,
            plan.code(),
            plan.label()
        );
    }

    private IdentityApi.UserSummary safeIdentity(UUID ownerId) {
        try {
            return identityApi.user(ownerId);
        } catch (Exception e) {
            return null;
        }
    }

    private String fallbackNameFromEmail(String email) {
        if (email == null || !email.contains("@")) return "User";
        String prefix = email.substring(0, email.indexOf('@')).replace('.', ' ').replace('_', ' ').trim();
        if (prefix.isEmpty()) return "User";
        return Character.toUpperCase(prefix.charAt(0)) + prefix.substring(1);
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "User";
        return fullName.trim().split("\\s+")[0];
    }

    private String formatCompactDisplayName(String fullName, String firstName, String explicitLastName) {
        String last = explicitLastName != null && !explicitLastName.isBlank() ? explicitLastName.trim() : "";
        if (last.isEmpty() && fullName != null) {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length >= 2) {
                last = parts[parts.length - 1];
            }
        }
        if (!last.isEmpty()) {
            return firstName + " " + Character.toUpperCase(last.charAt(0)) + ".";
        }
        return firstName;
    }

    private String resolveSafeVisaStatus(UUID ownerId, String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Decrypt AES-GCM ciphertext if FieldCipher formatted ("keyId.iv.payload")
        if (raw.split("\\.", 3).length == 3) {
            if (cipher == null) return null;
            try {
                return cipher.decrypt(raw, "account:" + ownerId);
            } catch (Exception e) {
                return null;
            }
        }
        return raw.length() <= 32 ? raw.trim() : null;
    }
}
