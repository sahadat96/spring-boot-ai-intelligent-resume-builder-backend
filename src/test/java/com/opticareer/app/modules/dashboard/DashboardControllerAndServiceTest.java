package com.opticareer.app.modules.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.opticareer.app.modules.account.AccountApi;
import com.opticareer.app.modules.analysis.AnalysisApi;
import com.opticareer.app.modules.analysis.AnalysisApi.CompletedAnalysisSnapshot;
import com.opticareer.app.modules.analysis.AnalysisApi.LatestStatus;
import com.opticareer.app.modules.dashboard.api.DashboardController;
import com.opticareer.app.modules.dashboard.application.DashboardService;
import com.opticareer.app.modules.dashboard.application.PlanInfoPort;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot;
import com.opticareer.app.modules.dashboard.domain.RoleMatchingEngine;
import com.opticareer.app.modules.identity.IdentityApi;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.web.ProblemHandler;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerAndServiceTest {

    private AccountApi accountApi;
    private IdentityApi identityApi;
    private AnalysisApi analysisApi;
    private PlanInfoPort planInfoPort;
    private FieldCipher fieldCipher;
    private DashboardService dashboardService;
    private MockMvc mockMvc;
    private UUID userId;
    private JwtAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        accountApi = mock(AccountApi.class);
        identityApi = mock(IdentityApi.class);
        analysisApi = mock(AnalysisApi.class);
        planInfoPort = mock(PlanInfoPort.class);

        String validB64Key = Base64.getEncoder().encodeToString(new byte[32]);
        AppProperties props = new AppProperties(
            "https://opticareer.co",
            "https://opticareer.co",
            new AppProperties.Security(validB64Key, validB64Key, "v1", true, "opticareer", "", 10, 14, false),
            new AppProperties.Storage("http://localhost:9000", "us-east-1", "bucket", "ak", "sk", 5, "localhost", 3310, false),
            new AppProperties.Ai("https://api.openai.com/v1", "", "gpt-4o", 30, 4, 1_000_000, false),
            new AppProperties.Stripe("", "", "https://api.stripe.com", "", "", "", "", false),
            new AppProperties.Worker(true, 500, 3, 120),
            "noreply@opticareer.co"
        );
        fieldCipher = new FieldCipher(props, "");

        dashboardService = new DashboardService(
            accountApi, identityApi, analysisApi, planInfoPort, new RoleMatchingEngine(), fieldCipher
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService))
            .setControllerAdvice(new ProblemHandler())
            .build();

        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok")
            .header("alg", "HS256")
            .subject(userId.toString())
            .claim("sid", UUID.randomUUID().toString())
            .build();
        authToken = new JwtAuthenticationToken(jwt);
    }

    @Test
    void dashboardWithCompletedAnalysis_populatesAllCardsAndDecryptsVisa() throws Exception {
        String encryptedVisa = fieldCipher.encrypt("OPT", "account:" + userId);
        when(accountApi.profile(userId)).thenReturn(new AccountApi.Profile(
            userId, "Emran Hossain", "", "", "Cloud & DevOps", "", "US", encryptedVisa, false
        ));
        when(identityApi.user(userId)).thenReturn(new IdentityApi.UserSummary(userId, "emran@example.com", true, "ACTIVE"));
        when(planInfoPort.currentPlan(userId)).thenReturn(new PlanInfoPort.PlanInfo("FREE", "Free plan"));

        UUID analysisId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(analysisApi.latestCompletedAnalysis(userId)).thenReturn(Optional.of(new CompletedAnalysisSnapshot(
            analysisId, fileId, "Emran_Resume.pdf", "DevOps Engineer",
            72, 76, 70, 70, 66,
            List.of("Strong Linux foundation", "AWS hands-on experience"),
            List.of("Add Docker and Kubernetes experience"),
            List.of("Linux", "AWS", "Python", "Git"),
            List.of("Docker", "Kubernetes", "Terraform", "CI/CD"),
            Map.of(),
            Map.of("SKILLS", "Linux, AWS, Python, Git", "EXPERIENCE", "DevOps Engineer deploying AWS EC2 and Linux pipelines"),
            "DevOps Engineer AWS Linux Bash Python Git",
            false, false, Instant.now()
        )));
        when(analysisApi.latestAnalysisStatus(userId)).thenReturn(Optional.of(
            new LatestStatus(analysisId, fileId, "Emran_Resume.pdf", "COMPLETED", Instant.now())
        ));

        mockMvc.perform(get("/api/v1/dashboard").principal(authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.firstName").value("Emran"))
            .andExpect(jsonPath("$.user.displayName").value("Emran H."))
            .andExpect(jsonPath("$.user.visaStatus").value("OPT"))
            .andExpect(jsonPath("$.user.planLabel").value("Free plan"))
            .andExpect(jsonPath("$.careerSnapshot.hasAnalysis").value(true))
            .andExpect(jsonPath("$.careerSnapshot.careerDirection").value("DevOps Engineer"))
            .andExpect(jsonPath("$.careerSnapshot.analysisStatusLabel").value("Analysis complete"))
            .andExpect(jsonPath("$.careerSnapshot.potentialLiftPoints").isNumber())
            .andExpect(jsonPath("$.biggestOpportunity.title").value("Biggest opportunity"))
            .andExpect(jsonPath("$.biggestOpportunity.missingSkills[0]").value("Docker"))
            .andExpect(jsonPath("$.profileHighlights").isNotEmpty())
            .andExpect(jsonPath("$.bestFitRoles.length()").value(3))
            .andExpect(jsonPath("$.recommendedNextMoves[0].id").value("skill-gap"));

        mockMvc.perform(get("/api/v1/dashboard/career-targets").param("limit", "5").principal(authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void dashboardBeforeAnyUpload_returnsGracefulEmptyState() throws Exception {
        when(accountApi.profile(userId)).thenReturn(new AccountApi.Profile(
            userId, "Sahadat Hossain", "Sahadat", "Hossain", "", "", "BD", null, false
        ));
        when(identityApi.user(userId)).thenReturn(new IdentityApi.UserSummary(userId, "sahadat@example.com", true, "ACTIVE"));
        when(planInfoPort.currentPlan(userId)).thenReturn(new PlanInfoPort.PlanInfo("FREE", "Free plan"));
        when(analysisApi.latestCompletedAnalysis(userId)).thenReturn(Optional.empty());
        when(analysisApi.latestAnalysisStatus(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/dashboard").principal(authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.firstName").value("Sahadat"))
            .andExpect(jsonPath("$.user.displayName").value("Sahadat H."))
            .andExpect(jsonPath("$.careerSnapshot.hasAnalysis").value(false))
            .andExpect(jsonPath("$.careerSnapshot.matchPercentage").value(0))
            .andExpect(jsonPath("$.careerSnapshot.analysisStatus").value("NONE"))
            .andExpect(jsonPath("$.bestFitRoles").isEmpty())
            .andExpect(jsonPath("$.recommendedNextMoves[0].route").value("/resume-analysis"));
    }

    @Test
    void dashboardWhileNewAnalysisQueued_showsInProgressWithLatestCompletedData() {
        when(accountApi.profile(userId)).thenReturn(new AccountApi.Profile(
            userId, "Emran Hossain", "", "", "", "", "", null, false
        ));
        when(identityApi.user(userId)).thenReturn(new IdentityApi.UserSummary(userId, "emran@example.com", true, "ACTIVE"));
        when(planInfoPort.currentPlan(userId)).thenReturn(new PlanInfoPort.PlanInfo("PRO", "Pro plan"));

        UUID oldId = UUID.randomUUID();
        UUID queuedId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(analysisApi.latestCompletedAnalysis(userId)).thenReturn(Optional.of(new CompletedAnalysisSnapshot(
            oldId, fileId, "old.pdf", "Backend Engineer",
            85, 88, 84, 82, null,
            List.of("Strong Java backend"), List.of(), List.of("Java", "Spring Boot"), List.of(),
            Map.of(), Map.of("SKILLS", "Java, Spring Boot"), "Java Spring Boot Backend Engineer",
            false, true, Instant.now().minusSeconds(3600)
        )));
        when(analysisApi.latestAnalysisStatus(userId)).thenReturn(Optional.of(
            new LatestStatus(queuedId, fileId, "new.pdf", "QUEUED", Instant.now())
        ));

        DashboardSnapshot snapshot = dashboardService.getDashboard(userId, 3);

        assertThat(snapshot.user().planLabel()).isEqualTo("Pro plan");
        assertThat(snapshot.careerSnapshot().hasAnalysis()).isTrue();
        assertThat(snapshot.careerSnapshot().analysisStatus()).isEqualTo("QUEUED");
        assertThat(snapshot.careerSnapshot().analysisStatusLabel()).isEqualTo("New analysis in progress");
        assertThat(snapshot.careerSnapshot().overallScore()).isEqualTo(85);
    }

    @Test
    void unauthenticatedDashboard_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
