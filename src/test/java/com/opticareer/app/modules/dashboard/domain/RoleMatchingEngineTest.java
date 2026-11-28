package com.opticareer.app.modules.dashboard.domain;

import static org.assertj.core.api.Assertions.*;

import com.opticareer.app.modules.analysis.AnalysisApi.CompletedAnalysisSnapshot;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BestFitRole;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BiggestOpportunity;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.RecommendedMove;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleMatchingEngineTest {

    private final RoleMatchingEngine engine = new RoleMatchingEngine();

    @Test
    void devopsResume_ranksCloudAndDevOpsRolesAndCalculatesOpportunityLift() {
        CompletedAnalysisSnapshot devopsAnalysis = new CompletedAnalysisSnapshot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Emran_Resume.pdf",
            null,
            78,
            82,
            75,
            76,
            null,
            List.of("Clean single-column layout", "Consistent bullet formatting"),
            List.of("Quantify impact in experience bullets"),
            List.of("Linux", "AWS", "Docker", "Bash", "Git", "Nginx"),
            List.of(),
            Map.of("ats", Map.of("details", List.of(Map.of(
                "rule", "KEYWORD_DENSITY", "pointsMax", 20, "pointsEarned", 10
            )))),
            Map.of(
                "SKILLS", "Linux, AWS, Docker, Bash, Git, Nginx, Networking, VPC, Monitoring",
                "EXPERIENCE", "Cloud & DevOps Engineer managing AWS EC2/VPC cloud infrastructure, Linux servers, Docker containers, Nginx and Bash automation"
            ),
            "Emran Hossain Cloud & DevOps Engineer AWS Linux Bash Docker Git Nginx VPC networking monitoring prometheus sre",
            false,
            false,
            Instant.now()
        );

        List<BestFitRole> top3 = engine.rankRoles(devopsAnalysis, null, 3);

        assertThat(top3).hasSize(3);
        assertThat(top3)
            .extracting(BestFitRole::domain)
            .containsOnly("CLOUD_DEVOPS");
        assertThat(top3)
            .extracting(BestFitRole::roleTitle)
            .containsAnyOf("DevOps Engineer", "Cloud Engineer", "Site Reliability Engineer", "Platform Engineer");

        String direction = engine.resolveCareerDirection(devopsAnalysis, null, top3);
        int matchPct = engine.calculateSnapshotMatch(devopsAnalysis, direction, top3);
        int lift = engine.calculatePotentialLift(devopsAnalysis, matchPct, top3.get(0).missingCoreSkills());
        BiggestOpportunity opp = engine.buildBiggestOpportunity(devopsAnalysis, direction, matchPct, lift, top3);
        List<String> highlights = engine.buildProfileHighlights(devopsAnalysis, top3);
        List<RecommendedMove> moves = engine.buildRecommendedMoves(devopsAnalysis, direction, top3, opp);

        assertThat(matchPct).isBetween(60, 95);
        assertThat(lift).isPositive();
        assertThat(opp.missingSkills()).containsAnyOf("Kubernetes", "Terraform", "CI/CD");
        assertThat(opp.matchLiftPercentage()).isBetween(1, lift);
        assertThat(opp.description()).contains("Add ");
        assertThat(highlights).isNotEmpty();
        assertThat(highlights.get(0)).startsWith("Strong ");
        assertThat(moves).extracting(RecommendedMove::id).contains("skill-gap", "career-targets", "resume-builder");
    }

    @Test
    void javaBackendResume_ranksJavaAndBackendRolesHighestAndRespectsExplicitTargetRole() {
        CompletedAnalysisSnapshot javaAnalysis = new CompletedAnalysisSnapshot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Sahadat_Resume.pdf",
            "Senior Backend Engineer",
            86,
            90,
            85,
            82,
            84,
            List.of("Strong Java backend skills"),
            List.of("Add Kubernetes experience"),
            List.of("Java", "Spring Boot", "PostgreSQL", "Redis", "Docker", "REST API"),
            List.of("Kubernetes"),
            Map.of(),
            Map.of(
                "SKILLS", "Java, Spring Boot, PostgreSQL, Redis, Docker, REST API, JUnit, Git",
                "EXPERIENCE", "Backend Engineer building Spring Boot microservices with PostgreSQL, Redis, Docker, Kafka, OAuth2"
            ),
            "Backend Engineer Java Spring Boot Spring PostgreSQL REST API Redis Docker JUnit Microservices Kafka OAuth2",
            false,
            false,
            Instant.now()
        );

        List<BestFitRole> top3 = engine.rankRoles(javaAnalysis, null, 3);
        String direction = engine.resolveCareerDirection(javaAnalysis, null, top3);

        assertThat(direction).isEqualTo("Senior Backend Engineer");
        assertThat(top3.get(0).roleTitle()).isIn("Backend Engineer", "Java / Spring Boot Engineer");
        assertThat(top3.get(0).matchPercentage()).isGreaterThanOrEqualTo(80);
    }
}
