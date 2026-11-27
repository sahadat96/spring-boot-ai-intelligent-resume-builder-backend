package com.opticareer.app.modules.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.opticareer.app.modules.analysis.api.ResumeAnalysisController;
import com.opticareer.app.modules.analysis.application.*;
import com.opticareer.app.modules.analysis.domain.*;
import com.opticareer.app.modules.analysis.infrastructure.ConfigurableLlmSuggestionAdapter;
import com.opticareer.app.modules.analysis.infrastructure.RedisAnalysisQueue;
import com.opticareer.app.modules.analysis.infrastructure.RedisAnalysisWorker;
import com.opticareer.app.modules.analysis.infrastructure.persistence.*;
import com.opticareer.app.modules.analysis.parsing.DocumentParserService;
import com.opticareer.app.modules.analysis.scoring.*;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.platform.web.ProblemHandler;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import com.opticareer.app.shared.event.EventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class ResumeAnalysisControllerAndQueueTest {

    private ResumeAnalysisService service;
    private MockMvc mockMvc;
    private JwtAuthenticationToken authToken;
    private UUID ownerId;
    private Json json;

    @BeforeEach
    void setUp() {
        service = mock(ResumeAnalysisService.class);
        json = new Json(new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new ResumeAnalysisController(service, json))
            .setControllerAdvice(new ProblemHandler())
            .build();
        ownerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok")
            .header("alg", "HS256")
            .subject(ownerId.toString())
            .claim("sid", UUID.randomUUID().toString())
            .build();
        authToken = new JwtAuthenticationToken(jwt);
    }

    @Test
    void uploadThenQueueAnalyzeThenPollAndDelete_fullLifecycle() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "Sahadat_Resume.pdf", "application/pdf", "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8)
        );

        when(service.uploadResume(eq(ownerId), any(), eq("application/pdf"), eq("Sahadat_Resume.pdf")))
            .thenReturn(new ResumeAnalysisService.UploadOutput(fileId, "Sahadat_Resume.pdf", 16, "application/pdf", "abc123", "UPLOADED"));

        mockMvc.perform(multipart("/api/v1/resume-analysis/upload").file(file).principal(authToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileId").value(fileId.toString()))
            .andExpect(jsonPath("$.status").value("UPLOADED"));

        ResumeAnalysisService.AnalysisView queuedView = new ResumeAnalysisService.AnalysisView(
            analysisId, fileId, "Sahadat_Resume.pdf", "Backend Engineer", "QUEUED", null,
            null, null, null, null, null,
            List.of(), List.of(), List.of(), List.of(), Map.of(),
            false, 0L, false, null, null, null, Instant.now()
        );
        when(service.enqueueAnalysis(eq(ownerId), eq(fileId), eq("Backend Engineer"), any()))
            .thenReturn(queuedView);

        mockMvc.perform(post("/api/v1/resume-analysis/{fileId}/analyze", fileId)
                .principal(authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetRole\":\"Backend Engineer\"}"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/v1/resume-analysis/" + analysisId))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.targetRole").value("Backend Engineer"));

        ScoreBreakdown ats = ScoreBreakdown.of("ATS Compatibility", List.of(
            ScoreDetail.pass("CONTACT_INFO", "Has email and phone", 10)
        ));
        ResumeAnalysisService.AnalysisView completedView = new ResumeAnalysisService.AnalysisView(
            analysisId, fileId, "Sahadat_Resume.pdf", "Backend Engineer", "COMPLETED", null,
            82, 88, 80, 78, 75,
            List.of("Strong experience"), List.of("Add more metrics"), List.of("Java", "Spring Boot"), List.of("Kubernetes"),
            Map.of(
                "ats", ats,
                "content", ats,
                "formatting", ats,
                "baselineStrengths", List.of("Strong experience"),
                "baselineImprovements", List.of("Add more metrics")
            ),
            false, 42L, false, null, null, null, Instant.now()
        );
        when(service.getAnalysis(ownerId, analysisId)).thenReturn(completedView);

        mockMvc.perform(get("/api/v1/resume-analysis/{id}", analysisId).principal(authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.overallScore").value(82))
            .andExpect(jsonPath("$.scoreDetails.ats.score").value(100))
            .andExpect(jsonPath("$.scoreDetails.ats.details[0].rule").value("CONTACT_INFO"));

        mockMvc.perform(delete("/api/v1/resume-analysis/files/{fileId}", fileId).principal(authToken))
            .andExpect(status().isNoContent());
        verify(service).deleteUpload(ownerId, fileId);

        mockMvc.perform(delete("/api/v1/resume-analysis/{id}", analysisId).principal(authToken))
            .andExpect(status().isNoContent());
        verify(service).deleteAnalysis(ownerId, analysisId);
    }

    @Test
    void unauthenticatedAnalyze_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "%PDF-1.4\nsample".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/resume-analysis/upload").file(file))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void processorGatesAiSuggestionsStrictlyToSubscribedUsers() {
        DocumentParserService parser = mock(DocumentParserService.class);
        ResumeScoreEngine engine = new ResumeScoreEngine(
            new AtsScoreCalculator(), new ContentScoreCalculator(), new FormattingScoreCalculator()
        );
        ResumeAnalysisJpaRepository analysisRepo = mock(ResumeAnalysisJpaRepository.class);
        ResumeUploadJpaRepository uploadRepo = mock(ResumeUploadJpaRepository.class);
        SubscriptionCheckerPort subscriptionChecker = mock(SubscriptionCheckerPort.class);
        AiSuggestionPort aiPort = mock(AiSuggestionPort.class);
        EventPublisher events = mock(EventPublisher.class);
        TransactionTemplate tx = immediateTx();

        UUID analysisId = UUID.randomUUID();
        ResumeAnalysisEntity analysisEntity = new ResumeAnalysisEntity();
        analysisEntity.setId(analysisId);
        analysisEntity.setOwnerId(ownerId);
        analysisEntity.setFileName("resume.pdf");
        analysisEntity.setFileSize(500);
        analysisEntity.setMimeType("application/pdf");

        when(analysisRepo.findByIdAndOwnerId(analysisId, ownerId)).thenReturn(Optional.of(analysisEntity));
        when(parser.parse(any(), eq("application/pdf"))).thenReturn(new ParsedDocument(
            "Sahadat Hossain\nsahadat@opticareer.co\nEXPERIENCE\n- Built REST API reducing latency by 40%\nSKILLS\nJava, Spring Boot",
            Map.of(ResumeSection.EXPERIENCE, "Built REST API", ResumeSection.SKILLS, "Java, Spring Boot"),
            List.of("Built REST API reducing latency by 40%"),
            1, false, "2.0.0", 12
        ));

        ResumeAnalysisProcessor processor = new ResumeAnalysisProcessor(
            parser, engine, analysisRepo, uploadRepo, subscriptionChecker, aiPort, events, tx
        );

        // 1) Free user -> AI must NEVER be invoked
        when(subscriptionChecker.isPremium(ownerId)).thenReturn(false);
        processor.executeAndPersist(analysisId, ownerId, new byte[]{1, 2, 3, 4}, "application/pdf", null, null, System.nanoTime());

        verifyNoInteractions(aiPort);
        assertThat(analysisEntity.isAiEnhanced()).isFalse();
        assertThat(analysisEntity.getStatus()).isEqualTo("COMPLETED");
        assertThat(analysisEntity.getOverallScore()).isNotNull();

        // 2) Subscribed/Premium user -> AI suggestion layer IS invoked, while scores stay algorithmic
        when(subscriptionChecker.isPremium(ownerId)).thenReturn(true);
        AiSuggestion aiSuggestion = new AiSuggestion(
            List.of("Strong quantifiable latency gain of 40%"),
            List.of("Expand summary to 40-60 words highlighting Spring Boot architecture"),
            List.of("Docker", "Kubernetes"),
            List.of(new AiSuggestion.BulletRewrite("Built REST API", "Architected Spring Boot REST API reducing p95 latency by 40%", "Adds technical specificity"))
        );
        when(aiPort.enhance(eq(ownerId), eq(analysisId), any(), any(), isNull(), isNull()))
            .thenReturn(new AiSuggestionPort.EnhancedOutput(aiSuggestion, "OPENAI", "gpt-4o"));

        processor.executeAndPersist(analysisId, ownerId, new byte[]{1, 2, 3, 4}, "application/pdf", null, null, System.nanoTime());

        verify(aiPort).enhance(eq(ownerId), eq(analysisId), any(), any(), isNull(), isNull());
        assertThat(analysisEntity.isAiEnhanced()).isTrue();
        assertThat(analysisEntity.getAiProvider()).isEqualTo("OPENAI");
        assertThat(analysisEntity.getAiModel()).isEqualTo("gpt-4o");
        assertThat(analysisEntity.getAreasToImprove()).contains("Expand summary to 40-60 words highlighting Spring Boot architecture");
        assertThat(analysisEntity.getMissingKeywords()).contains("Docker", "Kubernetes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisQueueAndWorker_processesQueuedJobAndSkipsRetriesOnNonRetryableDomainError() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);

        ResumeAnalysisProcessor processor = mock(ResumeAnalysisProcessor.class);
        AppProperties props = dummyProperties();

        RedisAnalysisQueue queue = new RedisAnalysisQueue(redis, processor, json, Runnable::run);
        AnalysisJob job = new AnalysisJob(UUID.randomUUID(), ownerId, UUID.randomUUID(), null, null);
        queue.enqueue(job);

        verify(listOps).rightPush(eq(RedisAnalysisQueue.QUEUE_KEY), contains(job.analysisId().toString()));

        ResumeAnalysisJpaRepository analysisRepo = mock(ResumeAnalysisJpaRepository.class);
        RedisAnalysisWorker worker = new RedisAnalysisWorker(redis, processor, json, Runnable::run, props, analysisRepo, immediateTx());

        // 1) Normal success
        when(listOps.leftPop(RedisAnalysisWorker.QUEUE_KEY))
            .thenReturn(json.write(job))
            .thenReturn(null);
        worker.poll();
        verify(processor).process(argThat(j -> j.analysisId().equals(job.analysisId())));

        // 2) Non-retryable DomainException -> markFailed immediately, do NOT re-push
        clearInvocations(listOps, processor);
        doThrow(new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Bad file"))
            .when(processor).process(argThat(j -> j.analysisId().equals(job.analysisId())));
        when(listOps.leftPop(RedisAnalysisWorker.QUEUE_KEY))
            .thenReturn(json.write(job))
            .thenReturn(null);
        worker.poll();
        verify(processor).markFailed(job.analysisId(), "Bad file");
        verify(listOps, never()).rightPush(any(), any());

        // 3) Stuck job recovery calls failStuckAnalyses
        worker.recoverStuckJobs();
        verify(analysisRepo).failStuckAnalyses(any(), any(), contains("worker lease timeout"));
    }

    @Test
    void configurableLlmAdapter_supportsSwitchingBetweenOpenAiAndGemini() {
        ConfigurableLlmSuggestionAdapter adapter = new ConfigurableLlmSuggestionAdapter(
            dummyProperties(),
            json,
            mock(FieldCipher.class),
            null
        );

        String openAi = invokeDetect(adapter, "https://api.openai.com/v1", "gpt-4o");
        String geminiNative = invokeDetect(adapter, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash");
        String geminiCompat = invokeDetect(adapter, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash");

        assertThat(openAi).isEqualTo("OPENAI");
        assertThat(geminiNative).isEqualTo("GEMINI");
        assertThat(geminiCompat).isEqualTo("OPENAI_COMPAT");
    }

    private String invokeDetect(ConfigurableLlmSuggestionAdapter adapter, String url, String model) {
        try {
            var m = ConfigurableLlmSuggestionAdapter.class.getDeclaredMethod("detectProvider", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(adapter, url, model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TransactionTemplate immediateTx() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }

    private AppProperties dummyProperties() {
        return new AppProperties(
            "https://opticareer.co",
            "https://opticareer.co",
            new AppProperties.Security("a".repeat(44), "b".repeat(44), "v1", true, "opticareer", "", 10, 14, false),
            new AppProperties.Storage("http://localhost:9000", "us-east-1", "bucket", "ak", "sk", 5, "localhost", 3310, false),
            new AppProperties.Ai("https://api.openai.com/v1", "", "gpt-4o", 30, 4, 1_000_000, false),
            new AppProperties.Stripe("", "", "https://api.stripe.com", "", "", "", "", false),
            new AppProperties.Worker(true, 500, 3, 120),
            "noreply@opticareer.co"
        );
    }
}
