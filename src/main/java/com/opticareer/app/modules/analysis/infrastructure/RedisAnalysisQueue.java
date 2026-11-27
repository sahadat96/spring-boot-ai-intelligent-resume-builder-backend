package com.opticareer.app.modules.analysis.infrastructure;

import com.opticareer.app.modules.analysis.application.AnalysisJob;
import com.opticareer.app.modules.analysis.application.AnalysisJobQueue;
import com.opticareer.app.modules.analysis.application.ResumeAnalysisProcessor;
import com.opticareer.app.platform.json.Json;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisAnalysisQueue implements AnalysisJobQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisAnalysisQueue.class);
    public static final String QUEUE_KEY = "opticareer:analysis:queue";

    private final @Nullable StringRedisTemplate redis;
    private final ResumeAnalysisProcessor processor;
    private final Json json;
    private final Executor analysisExecutor;

    public RedisAnalysisQueue(
            @Nullable StringRedisTemplate redis,
            ResumeAnalysisProcessor processor,
            Json json,
            @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.redis = redis;
        this.processor = processor;
        this.json = json;
        this.analysisExecutor = analysisExecutor;
    }

    @Override
    public void enqueue(AnalysisJob job) {
        try {
            if (redis != null) {
                redis.opsForList().rightPush(QUEUE_KEY, json.write(job));
                log.info("Enqueued resume analysis job [analysisId={}] to Redis queue '{}'",
                    job.analysisId(), QUEUE_KEY);
                return;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for analysis queue ({}). Falling back to async executor.", e.getMessage());
        }

        CompletableFuture.runAsync(() -> processor.process(job), analysisExecutor);
    }
}
