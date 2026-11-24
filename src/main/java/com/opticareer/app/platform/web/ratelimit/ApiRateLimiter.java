package com.opticareer.app.platform.web.ratelimit;

/**
 * Service interface for evaluating rate limits against callers.
 */
public interface ApiRateLimiter {

  /**
   * Evaluates the rate limit for a specific caller key within a time window.
   *
   * @param key           unique caller key (e.g. "ip:192.168.1.1" or "user:018f...")
   * @param limit         maximum requests allowed in window
   * @param windowSeconds duration of the rolling window in seconds
   * @return rate limit outcome with quota status and reset timing
   */
  RateLimitResult check(String key, long limit, long windowSeconds);
}
