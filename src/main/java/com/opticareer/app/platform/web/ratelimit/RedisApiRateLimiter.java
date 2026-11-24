package com.opticareer.app.platform.web.ratelimit;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Production Redis rate limiter implementing an atomic sliding-window counter via Lua script.
 * Enforces resilient fail-open behavior if Redis is temporarily unreachable.
 */
@Component
public class RedisApiRateLimiter implements ApiRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisApiRateLimiter.class);

  private final StringRedisTemplate redis;

  private static final DefaultRedisScript<List> SCRIPT =
      new DefaultRedisScript<>(
          """
          local current = redis.call('INCR', KEYS[1]);
          if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1]);
          end
          local ttl = redis.call('TTL', KEYS[1]);
          if ttl < 0 then
              ttl = tonumber(ARGV[1]);
              redis.call('EXPIRE', KEYS[1], ARGV[1]);
          end
          return { current, ttl }
          """,
          List.class);

  public RedisApiRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public RateLimitResult check(String key, long limit, long windowSeconds) {
    try {
      @SuppressWarnings("unchecked")
      List<Long> result =
          redis.execute(
              SCRIPT,
              List.of("throttle:" + key),
              String.valueOf(windowSeconds));

      if (result == null || result.size() < 2) {
        return new RateLimitResult(true, limit, limit, windowSeconds);
      }

      long current = ((Number) result.get(0)).longValue();
      long ttl = ((Number) result.get(1)).longValue();
      long remaining = Math.max(0, limit - current);
      boolean allowed = current <= limit;

      return new RateLimitResult(allowed, limit, remaining, ttl);
    } catch (RedisConnectionFailureException e) {
      // Fail-open strategy: Infrastructure blip must not bring down core application
      log.warn("Rate-limiter Redis unavailable (key: {}). Failing open to prevent API outage: {}", key, e.getMessage());
      return new RateLimitResult(true, limit, limit, 0);
    } catch (Exception e) {
      log.error("Unexpected error in rate limiter. Failing open: {}", e.getMessage());
      return new RateLimitResult(true, limit, limit, 0);
    }
  }
}
