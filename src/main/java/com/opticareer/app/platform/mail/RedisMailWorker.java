package com.opticareer.app.platform.mail;

import com.opticareer.app.platform.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RedisMailWorker {

  private static final Logger log = LoggerFactory.getLogger(RedisMailWorker.class);
  public static final String QUEUE_KEY = "opticareer:mail:queue";
  public static final String DLQ_KEY = "opticareer:mail:dead-letter";
  private static final int MAX_ATTEMPTS = 3;

  private final StringRedisTemplate redis;
  private final SmtpMailSender sender;
  private final Json json;

  public RedisMailWorker(
      @Nullable StringRedisTemplate redis,
      SmtpMailSender sender,
      Json json) {
    this.redis = redis;
    this.sender = sender;
    this.json = json;
  }

  @Scheduled(fixedDelay = 500)
  public void poll() {
    if (redis == null) {
      return;
    }

    try {
      // Drain up to 10 jobs per tick
      for (int i = 0; i < 10; i++) {
        String payload = redis.opsForList().leftPop(QUEUE_KEY);
        if (payload == null) {
          break;
        }
        process(payload);
      }
    } catch (Exception e) {
      log.debug("Redis mail worker poll error: {}", e.getMessage());
    }
  }

  private void process(String payload) {
    MailJob job = null;
    try {
      job = json.read(payload, MailJob.class);
      sender.deliver(job);
    } catch (Exception e) {
      log.error("Failed to deliver queued mail: {}", e.getMessage(), e);
      if (job != null && job.attempts() < MAX_ATTEMPTS) {
        MailJob retry = job.withIncrementedAttempts();
        try {
          redis.opsForList().rightPush(QUEUE_KEY, json.write(retry));
          log.warn("Re-queued failed mail job [{}] (attempt {}/{})", job.id(), retry.attempts(), MAX_ATTEMPTS);
        } catch (Exception ex) {
          log.error("Failed to re-queue mail job [{}]: {}", job.id(), ex.getMessage());
        }
      } else if (job != null) {
        try {
          redis.opsForList().rightPush(DLQ_KEY, payload);
          log.error("Max retry attempts reached for mail job [{}]. Moved to DLQ '{}'", job.id(), DLQ_KEY);
        } catch (Exception ex) {
          log.error("Failed to push to DLQ: {}", ex.getMessage());
        }
      }
    }
  }
}
