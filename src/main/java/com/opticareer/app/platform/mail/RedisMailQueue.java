package com.opticareer.app.platform.mail;

import com.opticareer.app.platform.json.Json;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RedisMailQueue implements MailPort {

  private static final Logger log = LoggerFactory.getLogger(RedisMailQueue.class);
  public static final String QUEUE_KEY = "opticareer:mail:queue";

  private final StringRedisTemplate redis;
  private final SmtpMailSender sender;
  private final Json json;
  private final Executor mailExecutor;

  public RedisMailQueue(
      @Nullable StringRedisTemplate redis,
      SmtpMailSender sender,
      Json json,
      @Qualifier("mailExecutor") Executor mailExecutor) {
    this.redis = redis;
    this.sender = sender;
    this.json = json;
    this.mailExecutor = mailExecutor;
  }

  @Override
  public void send(String email, String subject, String text) {
    enqueue(new MailJob(email, subject, text));
  }

  @Override
  public void sendHtml(String email, String subject, String plain, String html) {
    enqueue(new MailJob(email, subject, plain, html));
  }

  private void enqueue(MailJob job) {
    try {
      if (redis != null) {
        String payload = json.write(job);
        redis.opsForList().rightPush(QUEUE_KEY, payload);
        log.info("Enqueued email job [{}] to Redis queue '{}' for {}", job.id(), QUEUE_KEY, job.email());
        return;
      }
    } catch (Exception e) {
      log.warn("Redis unavailable for mail queue ({}). Falling back to asynchronous thread pool.", e.getMessage());
    }

    // Non-blocking fallback to async executor if Redis is unreachable
    CompletableFuture.runAsync(() -> sender.deliver(job), mailExecutor);
  }
}
