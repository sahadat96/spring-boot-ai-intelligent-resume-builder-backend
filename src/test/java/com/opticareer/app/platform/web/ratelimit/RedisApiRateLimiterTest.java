package com.opticareer.app.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisApiRateLimiterTest {

  private StringRedisTemplate redis;
  private RedisApiRateLimiter limiter;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    limiter = new RedisApiRateLimiter(redis);
  }

  @Test
  @DisplayName("Rate check under limit returns allowed=true with correct remaining quota")
  void checkUnderLimit() {
    when(redis.execute(any(RedisScript.class), anyList(), anyString()))
        .thenReturn(List.of(15L, 45L)); // 15 requests made, 45 seconds remaining TTL

    RateLimitResult result = limiter.check("ip:127.0.0.1", 60, 60);

    assertThat(result.allowed()).isTrue();
    assertThat(result.limit()).isEqualTo(60);
    assertThat(result.remaining()).isEqualTo(45);
    assertThat(result.resetSeconds()).isEqualTo(45);
  }

  @Test
  @DisplayName("Rate check exceeding limit returns allowed=false with zero remaining quota")
  void checkExceedingLimit() {
    when(redis.execute(any(RedisScript.class), anyList(), anyString()))
        .thenReturn(List.of(61L, 30L)); // 61 requests made (over 60 limit), 30s remaining

    RateLimitResult result = limiter.check("ip:127.0.0.1", 60, 60);

    assertThat(result.allowed()).isFalse();
    assertThat(result.limit()).isEqualTo(60);
    assertThat(result.remaining()).isEqualTo(0);
    assertThat(result.resetSeconds()).isEqualTo(30);
  }

  @Test
  @DisplayName("Redis outage triggers fail-open resilience (allows request to prevent total outage)")
  void redisFailureFailsOpen() {
    when(redis.execute(any(RedisScript.class), anyList(), anyString()))
        .thenThrow(new RedisConnectionFailureException("Connection refused"));

    RateLimitResult result = limiter.check("ip:127.0.0.1", 60, 60);

    assertThat(result.allowed()).isTrue();
    assertThat(result.limit()).isEqualTo(60);
    assertThat(result.remaining()).isEqualTo(60);
  }
}
