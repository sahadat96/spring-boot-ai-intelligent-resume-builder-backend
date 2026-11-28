package com.opticareer.app.modules.dashboard.api;

import com.opticareer.app.modules.dashboard.application.DashboardService;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BestFitRole;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BiggestOpportunity;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.CareerSnapshot;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.RecommendedMove;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.UserSummary;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "Dashboard",
    description = """
        Aggregated career snapshot, role matching, biggest opportunity lift, profile highlights, and recommended next actions computed from the authenticated user's profile, billing plan, and latest completed resume analysis.
        """)
@Validated
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    // --- OpenAPI / Swagger Response Schemas ---

    @Schema(name = "DashboardUserDto", description = "Authenticated user display metadata for header greeting and sidebar footer")
    public record DashboardUserDto(
        @Schema(description = "Unique user UUID", example = "01937f4a-1234-7000-8000-000000000001")
        UUID userId,
        @Schema(description = "First name for greeting ('Good morning, Emran')", example = "Emran")
        String firstName,
        @Schema(description = "Compact sidebar display name", example = "Emran H.")
        String displayName,
        @Schema(description = "Full user name", example = "Emran Hossain")
        String fullName,
        @Schema(description = "Profile headline if set")
        String headline,
        @Schema(description = "Optional visa status label if present (e.g. OPT, F-1); null if unset", example = "OPT")
        String visaStatus,
        @Schema(description = "Subscription plan code", example = "FREE", allowableValues = {"FREE", "PRO", "PLUS"})
        String planCode,
        @Schema(description = "Human-readable plan label", example = "Free plan")
        String planLabel
    ) {}

    @Schema(name = "CareerSnapshotDto", description = "Hero career snapshot metrics derived from the latest completed resume analysis")
    public record CareerSnapshotDto(
        @Schema(description = "True when at least one resume analysis has completed", example = "true")
        boolean hasAnalysis,
        @Schema(description = "Latest completed analysis UUID (null if none)", example = "01937f4b-5678-7000-8000-000000000002")
        UUID latestAnalysisId,
        @Schema(description = "Latest uploaded resume file UUID (for direct re-analysis or skill gap navigation)", example = "01937f4a-1234-7000-8000-000000000001")
        UUID latestFileId,
        @Schema(description = "Latest analyzed resume filename", example = "Sahadat_Resume.pdf")
        String latestFileName,
        @Schema(description = "Primary career direction (explicit targetRole if provided, else #1 best-fit role)", example = "DevOps Engineer")
        String careerDirection,
        @Schema(description = "Overall career readiness match percentage (0-100) powering the circular ring", example = "68")
        int matchPercentage,
        @Schema(description = "Readiness tier badge key", example = "MODERATE", allowableValues = {"HIGH", "MODERATE", "EARLY", "NONE"})
        String readinessLevel,
        @Schema(description = "Human-readable readiness badge text", example = "Moderate readiness")
        String readinessLabel,
        @Schema(description = "Latest analysis status across user history", example = "COMPLETED", allowableValues = {"COMPLETED", "QUEUED", "PROCESSING", "FAILED", "NONE"})
        String analysisStatus,
        @Schema(description = "Status badge copy", example = "Analysis complete")
        String analysisStatusLabel,
        @Schema(description = "Estimated achievable match score gain (+X possible pill)", example = "18")
        int potentialLiftPoints,
        @Schema(description = "Latest resume overall ATS score (0-100); null if no analysis yet", example = "82")
        Integer overallScore,
        @Schema(description = "Latest ATS compatibility sub-score (0-100)", example = "88")
        Integer atsScore,
        @Schema(description = "Latest content quality sub-score (0-100)", example = "80")
        Integer contentScore,
        @Schema(description = "Latest formatting & structure sub-score (0-100)", example = "78")
        Integer formattingScore,
        @Schema(description = "Whether OCR was required during latest parse", example = "false")
        boolean ocrUsed,
        @Schema(description = "Whether AI suggestions were generated on latest analysis", example = "false")
        boolean aiEnhanced,
        @Schema(description = "UTC timestamp of latest completed analysis")
        Instant analyzedAt
    ) {}

    @Schema(name = "BiggestOpportunityDto", description = "Highest-impact improvement recommendation and projected match lift")
    public record BiggestOpportunityDto(
        @Schema(description = "Card heading", example = "Biggest opportunity")
        String title,
        @Schema(description = "Actionable summary highlighting missing core skills or highest-deficit rule", example = "Add Docker, Kubernetes, Terraform & CI/CD. One project or achievement bullet covering these closes the gaps recruiters flag most often for DevOps Engineer roles.")
        String description,
        @Schema(description = "Projected match percentage boost (+X% MATCH LIFT)", example = "15")
        int matchLiftPercentage,
        @ArraySchema(schema = @Schema(description = "Top missing high-impact skills", example = "Docker"))
        List<String> missingSkills,
        @Schema(description = "Largest point-deficit scoring rule code (e.g. MEASURABLE_RESULTS, KEYWORD_DENSITY)", example = "KEYWORD_DENSITY")
        String topMissingRule,
        @Schema(description = "Call-to-action button label", example = "View skill gaps")
        String ctaLabel,
        @Schema(description = "Frontend route for CTA", example = "/skill-gap")
        String ctaRoute
    ) {}

    @Schema(name = "BestFitRoleDto", description = "Ranked career role compatibility computed against candidate resume skills and experience")
    public record BestFitRoleDto(
        @Schema(description = "Target job role title", example = "DevOps Engineer")
        String roleTitle,
        @Schema(description = "Role domain category", example = "CLOUD_DEVOPS")
        String domain,
        @Schema(description = "Percentage compatibility bar value (0-100)", example = "85")
        int matchPercentage,
        @ArraySchema(schema = @Schema(description = "Matched candidate skills for this role", example = "Linux"))
        List<String> matchedSkills,
        @ArraySchema(schema = @Schema(description = "Missing core skills to improve match percentage", example = "Kubernetes"))
        List<String> missingCoreSkills
    ) {}

    @Schema(name = "RecommendedMoveDto", description = "Prioritized next step navigation card")
    public record RecommendedMoveDto(
        @Schema(description = "Action identifier", example = "skill-gap")
        String id,
        @Schema(description = "Card title", example = "Go to Skill Gap")
        String title,
        @Schema(description = "Supporting subtitle", example = "See exactly what to improve")
        String subtitle,
        @Schema(description = "Target frontend route", example = "/skill-gap")
        String route
    ) {}

    @Schema(name = "DashboardResponse", description = "Complete workspace overview dashboard payload")
    public record DashboardResponse(
        @Schema(description = "Authenticated user display metadata and plan tier")
        DashboardUserDto user,
        @Schema(description = "Top career readiness snapshot card")
        CareerSnapshotDto careerSnapshot,
        @Schema(description = "Biggest opportunity and match-lift callout card")
        BiggestOpportunityDto biggestOpportunity,
        @ArraySchema(schema = @Schema(description = "Verified strengths and domain highlights from resume evidence", example = "Strong Linux foundation"))
        List<String> profileHighlights,
        @ArraySchema(schema = @Schema(implementation = BestFitRoleDto.class))
        List<BestFitRoleDto> bestFitRoles,
        @ArraySchema(schema = @Schema(implementation = RecommendedMoveDto.class))
        List<RecommendedMoveDto> recommendedNextMoves
    ) {}

    // --- Endpoints ---

    @Operation(
        summary = "Get career workspace dashboard",
        description = "Returns the complete dashboard payload (career snapshot, match %, biggest opportunity, profile highlights, top matching job roles, and recommended next moves) computed deterministically from the user's profile, billing plan, and latest completed resume analysis. When no resume has been analyzed yet, returns a valid onboarding snapshot with `hasAnalysis: false`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard snapshot retrieved.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @Parameter(description = "Number of best-fit roles to return (1-10, default 3)", example = "3")
            @RequestParam(value = "topRoles", defaultValue = "3") @Min(1) @Max(10) int topRoles,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        DashboardSnapshot snapshot = service.getDashboard(ownerId, topRoles);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(toResponse(snapshot));
    }

    @Operation(
        summary = "List ranked best-fit career targets",
        description = "Evaluates the latest completed resume against all supported industry role ontologies and returns ranked role matches with matched skills and missing core skills (powers Career Targets & Skill Gap views).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ranked career targets retrieved.",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = BestFitRoleDto.class)))),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/career-targets")
    public ResponseEntity<List<BestFitRoleDto>> careerTargets(
            @Parameter(description = "Maximum number of roles to return (1-24, default 6)", example = "6")
            @RequestParam(value = "limit", defaultValue = "6") @Min(1) @Max(24) int limit,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        List<BestFitRoleDto> roles = service.getCareerTargets(ownerId, limit).stream()
            .map(this::toRoleDto)
            .toList();
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(roles);
    }

    // --- Mapping Helpers ---

    private DashboardResponse toResponse(DashboardSnapshot s) {
        UserSummary u = s.user();
        CareerSnapshot c = s.careerSnapshot();
        BiggestOpportunity o = s.biggestOpportunity();

        return new DashboardResponse(
            new DashboardUserDto(
                u.userId(), u.firstName(), u.displayName(), u.fullName(),
                u.headline(), u.visaStatus(), u.planCode(), u.planLabel()
            ),
            new CareerSnapshotDto(
                c.hasAnalysis(), c.latestAnalysisId(), c.latestFileId(), c.latestFileName(),
                c.careerDirection(), c.matchPercentage(), c.readinessLevel(), c.readinessLabel(),
                c.analysisStatus(), c.analysisStatusLabel(), c.potentialLiftPoints(),
                c.overallScore(), c.atsScore(), c.contentScore(), c.formattingScore(),
                c.ocrUsed(), c.aiEnhanced(), c.analyzedAt()
            ),
            new BiggestOpportunityDto(
                o.title(), o.description(), o.matchLiftPercentage(), o.missingSkills(),
                o.topMissingRule(), o.ctaLabel(), o.ctaRoute()
            ),
            s.profileHighlights(),
            s.bestFitRoles().stream().map(this::toRoleDto).toList(),
            s.recommendedNextMoves().stream()
                .map(m -> new RecommendedMoveDto(m.id(), m.title(), m.subtitle(), m.route()))
                .toList()
        );
    }

    private BestFitRoleDto toRoleDto(BestFitRole r) {
        return new BestFitRoleDto(
            r.roleTitle(), r.domain(), r.matchPercentage(), r.matchedSkills(), r.missingCoreSkills()
        );
    }

    private UUID requireOwner(JwtAuthenticationToken token) {
        if (token == null) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        return UUID.fromString(token.getName());
    }
}
