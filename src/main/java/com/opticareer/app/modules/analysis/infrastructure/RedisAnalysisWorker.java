package com.opticareer.app.modules.analysis.infrastructure;

import com.opticareer.app.modules.analysis.application.AnalysisJob;
import com.opticareer.app.modules.analysis.application.ResumeAnalysisProcessor;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeAnalysisJpaRepository;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RedisAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(RedisAnalysisWorker.class);
    public static final String QUEUE_KEY = RedisAnalysisQueue.QUEUE_KEY;
    public static final String DLQ_KEY = "opticareer:analysis:dead-letter";

    private final @Nullable StringRedisTemplate redis;
    private final ResumeAnalysisProcessor processor;
    private final Json json;
    private final Executor analysisExecutor;
    private final boolean enabled;
    private final int maxAttempts;
    private final int leaseSeconds;
    private final @Nullable ResumeAnalysisJpaRepository analysisRepo;
    private final @Nullable TransactionTemplate tx;

    public RedisAnalysisWorker(
            @Nullable StringRedisTemplate redis,
            ResumeAnalysisProcessor processor,
            Json json,
            @Qualifier("analysisExecutor") Executor analysisExecutor,
            AppProperties properties) {
        this(redis, processor, json, analysisExecutor, properties, null, null);
    }

    @Autowired
    public RedisAnalysisWorker(
            @Nullable StringRedisTemplate redis,
            ResumeAnalysisProcessor processor,
            Json json,
            @Qualifier("analysisExecutor") Executor analysisExecutor,
            AppProperties properties,
            @Nullable ResumeAnalysisJpaRepository analysisRepo,
            @Nullable TransactionTemplate tx) {
        this.redis = redis;
        this.processor = processor;
        this.json = json;
        this.analysisExecutor = analysisExecutor;
        this.enabled = properties.worker().enabled();
        this.maxAttempts = Math.min(properties.worker().maxAttempts(), 3);
        this.leaseSeconds = properties.worker().leaseSeconds();
        this.analysisRepo = analysisRepo;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-ms:500}")
    public void poll() {
        if (!enabled || redis == null) {
            return;
        }
        try {
            for (int i = 0; i < 4; i++) {
                String payload = redis.opsForList().leftPop(QUEUE_KEY);
                if (payload == null) {
                    break;
                }
                CompletableFuture.runAsync(() -> handlePayload(payload), analysisExecutor);
            }
        } catch (Exception e) {
            log.debug("Redis analysis worker poll error: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void recoverStuckJobs() {
        if (!enabled || analysisRepo == null || tx == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            Instant cutoff = now.minusSeconds(leaseSeconds);
            Integer recovered = tx.execute(status -> analysisRepo.failStuckAnalyses(
                cutoff, now, "Analysis job exceeded worker lease timeout; please click Analyze Again."));
            if (recovered != null && recovered > 0) {
                log.warn("Marked {} timed-out resume analysis job(s) as FAILED after {}s lease expiry",
                    recovered, leaseSeconds);
            }
        } catch (Exception e) {
            log.debug("Stuck analysis sweep skipped: {}", e.getMessage());
        }
    }

    void handlePayload(String payload) {
        AnalysisJob job = null;
        try {
            job = json.read(payload, AnalysisJob.class);
            processor.process(job);
        } catch (DomainException de) {
            // Deterministic domain/validation failures (bad file format, empty PDF, missing file) must not retry
            if (isNonRetryable(de.code()) || job == null) {
                if (job != null) {
                    processor.markFailed(job.analysisId(), de.getMessage());
                }
                log.warn("Non-retryable analysis failure [analysisId={}]: {}",
                    job != null ? job.analysisId() : null, de.getMessage());
                return;
            }
            handleRetryOrDlq(job, payload, de);
        } catch (Exception e) {
            log.error("Failed to process queued analysis job: {}", e.getMessage(), e);
            if (job != null) {
                handleRetryOrDlq(job, payload, e);
            }
        }
    }

    private void handleRetryOrDlq(AnalysisJob job, String payload, Exception cause) {
        if (job.attempts() < maxAttempts && redis != null) {
            AnalysisJob retry = job.withIncrementedAttempts();
            try {
                redis.opsForList().rightPush(QUEUE_KEY, json.write(retry));
                log.warn("Re-queued analysis job [analysisId={}] (attempt {}/{})",
                    job.analysisId(), retry.attempts(), maxAttempts);
                return;
            } catch (Exception ex) {
                log.error("Failed to re-queue analysis job [{}]: {}", job.analysisId(), ex.getMessage());
            }
        }
        processor.markFailed(job.analysisId(), "Analysis worker failed after retries: " + cause.getMessage());
        if (redis != null) {
            try {
                redis.opsForList().rightPush(DLQ_KEY, payload);
            } catch (Exception ex) {
                log.error("Failed to push analysis job to DLQ: {}", ex.getMessage());
            }
        }
    }

    private boolean isNonRetryable(ErrorCode code) {
        return code == ErrorCode.UNSUPPORTED_FORMAT
            || code == ErrorCode.VALIDATION_FAILED
            || code == ErrorCode.NOT_FOUND
            || code == ErrorCode.ANALYSIS_FAILED;
    }
}
