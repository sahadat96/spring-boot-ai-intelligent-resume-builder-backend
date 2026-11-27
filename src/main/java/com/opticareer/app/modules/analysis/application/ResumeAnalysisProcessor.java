package com.opticareer.app.modules.analysis.application;

import com.opticareer.app.modules.analysis.domain.*;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeAnalysisEntity;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeAnalysisJpaRepository;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeUploadEntity;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeUploadJpaRepository;
import com.opticareer.app.modules.analysis.parsing.DocumentParserService;
import com.opticareer.app.modules.analysis.scoring.ResumeScoreEngine;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import com.opticareer.app.shared.event.EventPublisher;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes queued resume analysis jobs cleanly outside long DB transactions:
 * 1) Short TX: mark PROCESSING & load upload bytes
 * 2) Pure CPU / OCR / optional Premium LLM work (no DB connection held)
 * 3) Short TX: persist COMPLETED scores, rules breakdown, and optional AI enhancement
 */
@Service
public class ResumeAnalysisProcessor {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisProcessor.class);

    private final DocumentParserService parser;
    private final ResumeScoreEngine scoreEngine;
    private final ResumeAnalysisJpaRepository analysisRepo;
    private final ResumeUploadJpaRepository uploadRepo;
    private final SubscriptionCheckerPort subscriptionChecker;
    private final AiSuggestionPort aiSuggestionPort;
    private final EventPublisher events;
    private final TransactionTemplate tx;

    private record LoadedJob(byte[] bytes, String mimeType) {}

    public ResumeAnalysisProcessor(
            DocumentParserService parser,
            ResumeScoreEngine scoreEngine,
            ResumeAnalysisJpaRepository analysisRepo,
            ResumeUploadJpaRepository uploadRepo,
            SubscriptionCheckerPort subscriptionChecker,
            AiSuggestionPort aiSuggestionPort,
            EventPublisher events,
            TransactionTemplate tx) {
        this.parser = parser;
        this.scoreEngine = scoreEngine;
        this.analysisRepo = analysisRepo;
        this.uploadRepo = uploadRepo;
        this.subscriptionChecker = subscriptionChecker;
        this.aiSuggestionPort = aiSuggestionPort;
        this.events = events;
        this.tx = tx;
    }

    public void process(AnalysisJob job) {
        long startedAt = System.nanoTime();
        try {
            LoadedJob loaded = tx.execute(status -> {
                ResumeAnalysisEntity entity = analysisRepo.findByIdAndOwnerId(job.analysisId(), job.ownerId())
                    .orElseThrow(() -> DomainException.missing("Analysis"));
                entity.setStatus(AnalysisStatus.PROCESSING.name());
                analysisRepo.save(entity);

                ResumeUploadEntity upload = uploadRepo.findByIdAndOwnerId(job.fileId(), job.ownerId())
                    .orElseThrow(() -> DomainException.missing("Uploaded resume file"));
                return new LoadedJob(upload.getContentBytes(), upload.getMimeType());
            });

            if (loaded == null) {
                throw new DomainException(ErrorCode.ANALYSIS_FAILED, "Unable to load queued resume job.");
            }

            executeAndPersist(
                job.analysisId(),
                job.ownerId(),
                loaded.bytes(),
                loaded.mimeType(),
                job.targetRole(),
                job.jobDescription(),
                startedAt
            );
        } catch (Exception e) {
            markFailed(job.analysisId(), e.getMessage());
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new DomainException(ErrorCode.ANALYSIS_FAILED, e.getMessage());
        }
    }

    public void executeAndPersist(
            UUID analysisId,
            UUID ownerId,
            byte[] bytes,
            String mimeType,
            String targetRole,
            String jobDescription,
            long startedAt) {
        ParsedDocument parsed = parser.parse(bytes, mimeType);
        if (parsed.rawText() == null || parsed.rawText().isBlank()) {
            throw new DomainException(ErrorCode.ANALYSIS_FAILED, "Could not extract readable text from the uploaded file.");
        }

        AnalysisResult result = scoreEngine.analyze(parsed, targetRole, jobDescription);

        // AI enhancement strictly post-scoring & only for subscribed/premium users
        AiSuggestionPort.EnhancedOutput aiOutput = null;
        if (subscriptionChecker.isPremium(ownerId)) {
            aiOutput = aiSuggestionPort.enhance(ownerId, analysisId, parsed, result, targetRole, jobDescription);
        }

        long totalMs = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000);
        final AiSuggestionPort.EnhancedOutput finalAiOutput = aiOutput;

        tx.executeWithoutResult(status -> {
            ResumeAnalysisEntity entity = analysisRepo.findByIdAndOwnerId(analysisId, ownerId)
                .orElseThrow(() -> DomainException.missing("Analysis"));

            entity.setStatus(AnalysisStatus.COMPLETED.name());
            entity.setErrorMessage(null);
            entity.setOverallScore(result.overallScore());
            entity.setAtsScore(result.atsScore());
            entity.setContentScore(result.contentScore());
            entity.setFormattingScore(result.formattingScore());
            entity.setJdMatchScore(result.jdMatchScore());

            if (finalAiOutput != null && finalAiOutput.suggestion() != null) {
                AiSuggestion s = finalAiOutput.suggestion();
                entity.setAiEnhanced(true);
                entity.setAiProvider(finalAiOutput.provider());
                entity.setAiModel(finalAiOutput.model());
                entity.setAiSuggestions(s);

                // Provide AI-enriched strengths/improvements alongside baseline keywords & missing keyword gaps
                entity.setStrengths(nonEmptyOrFallback(s.enhancedStrengths(), result.strengths()));
                entity.setAreasToImprove(nonEmptyOrFallback(s.enhancedImprovements(), result.areasToImprove()));
                entity.setKeywords(result.keywords());
                entity.setMissingKeywords(mergeUnique(result.missingKeywords(), s.keywordGaps(), 12));
            } else {
                entity.setAiEnhanced(false);
                entity.setAiProvider(null);
                entity.setAiModel(null);
                entity.setAiSuggestions(null);
                entity.setStrengths(result.strengths());
                entity.setAreasToImprove(result.areasToImprove());
                entity.setKeywords(result.keywords());
                entity.setMissingKeywords(result.missingKeywords());
            }

            Map<String, Object> detailsMap = new LinkedHashMap<>();
            detailsMap.put("ats", result.atsBreakdown());
            detailsMap.put("content", result.contentBreakdown());
            detailsMap.put("formatting", result.formattingBreakdown());
            detailsMap.put("baselineStrengths", result.strengths());
            detailsMap.put("baselineImprovements", result.areasToImprove());
            entity.setScoreDetails(detailsMap);

            Map<String, String> sectionMap = new LinkedHashMap<>();
            result.sections().forEach((k, v) -> sectionMap.put(k.name(), v));
            entity.setSections(sectionMap);

            String textToStore = parsed.rawText();
            if (textToStore.length() > 50_000) {
                textToStore = textToStore.substring(0, 50_000);
            }
            entity.setExtractedText(textToStore);
            entity.setOcrUsed(result.ocrUsed());
            entity.setParserVersion(result.parserVersion());
            entity.setProcessingMs(totalMs);

            analysisRepo.save(entity);
            events.publish(ownerId, "ResumeAnalysisCompleted", Map.of(
                "analysisId", analysisId,
                "overallScore", result.overallScore(),
                "aiEnhanced", entity.isAiEnhanced()
            ));
        });

        log.info("Resume analysis completed: id={} owner={} overall={} ats={} content={} formatting={} ai={} time={}ms",
            analysisId, ownerId, result.overallScore(), result.atsScore(),
            result.contentScore(), result.formattingScore(), aiOutput != null, totalMs);
    }

    public void markFailed(UUID analysisId, String message) {
        tx.executeWithoutResult(status -> analysisRepo.findById(analysisId).ifPresent(entity -> {
            entity.setStatus(AnalysisStatus.FAILED.name());
            String truncated = message != null && message.length() > 490 ? message.substring(0, 490) : message;
            entity.setErrorMessage(truncated != null ? truncated : "Analysis failed.");
            analysisRepo.save(entity);
        }));
    }

    private List<String> nonEmptyOrFallback(List<String> primary, List<String> fallback) {
        return (primary != null && !primary.isEmpty()) ? primary : fallback;
    }

    private List<String> mergeUnique(List<String> first, List<String> second, int limit) {
        Set<String> merged = new LinkedHashSet<>();
        if (first != null) merged.addAll(first);
        if (second != null) merged.addAll(second);
        return merged.stream().limit(limit).toList();
    }
}
