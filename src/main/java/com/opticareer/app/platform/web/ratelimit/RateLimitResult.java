package com.opticareer.app.platform.web.ratelimit;

/**
 * Result of an API rate limit check.
 *
 * @param allowed       whether the request is permitted to proceed
 * @param limit         maximum requests allowed in the window
 * @param remaining     remaining quota in the current window
 * @param resetSeconds  seconds remaining until quota window resets
 */
public record RateLimitResult(
    boolean allowed,
    long limit,
    long remaining,
    long resetSeconds) {}
