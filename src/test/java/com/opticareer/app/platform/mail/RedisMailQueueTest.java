package com.opticareer.app.platform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.opticareer.app.platform.json.Json;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RedisMailQueueTest {

  private StringRedisTemplate redis;
  private ListOperations<String, String> listOps;
  private SmtpMailSender sender;
  private Json json;
  private Executor executor;
  private RedisMailQueue queue;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    listOps = mock(ListOperations.class);
    when(redis.opsForList()).thenReturn(listOps);

    sender = mock(SmtpMailSender.class);
    json = new Json(new ObjectMapper());
    executor = Runnable::run; // direct execution in tests

    queue = new RedisMailQueue(redis, sender, json, executor);
  }

  @Test
  @DisplayName("Enqueue pushes serialized MailJob into Redis queue")
  void enqueuePushesToRedis() {
    queue.send("user@example.com", "Test Subject", "Test Body");

    verify(listOps).rightPush(eq(RedisMailQueue.QUEUE_KEY), argThat(payload -> {
      MailJob job = json.read(payload, MailJob.class);
      return "user@example.com".equals(job.email())
          && "Test Subject".equals(job.subject())
          && "Test Body".equals(job.text());
    }));
    verifyNoInteractions(sender);
  }

  @Test
  @DisplayName("Fallback executes asynchronously via executor when Redis is null")
  void fallbackWhenRedisNull() {
    RedisMailQueue fallbackQueue = new RedisMailQueue(null, sender, json, executor);

    fallbackQueue.send("fallback@example.com", "Fallback Subject", "Fallback Body");

    verify(sender).deliver(argThat(job -> "fallback@example.com".equals(job.email())));
  }

  @Test
  @DisplayName("RedisMailWorker pops and delivers jobs from Redis queue")
  void workerPopsAndDelivers() {
    MailJob job = new MailJob("worker@example.com", "Worker Subject", "Worker Body");
    String payload = json.write(job);

    when(listOps.leftPop(RedisMailWorker.QUEUE_KEY))
        .thenReturn(payload)
        .thenReturn(null);

    RedisMailWorker worker = new RedisMailWorker(redis, sender, json);
    worker.poll();

    verify(sender).deliver(argThat(j -> "worker@example.com".equals(j.email())));
  }
}
