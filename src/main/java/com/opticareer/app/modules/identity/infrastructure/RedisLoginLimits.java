package com.opticareer.app.modules.identity.infrastructure;

import com.opticareer.app.modules.identity.application.IdentityPorts;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.shared.error.*;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisLoginLimits implements IdentityPorts.LoginLimits {
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
          Long.class);

  public RedisLoginLimits(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void attempt(String email) {
    limit("login:", email, 10, 60, "Try signing in again in one minute.");
  }

  public void verification(String email) {
    limit("verification:", email, 3, 3600, "Try requesting another verification email later.");
  }

  private void limit(String prefix, String email, int maximum, int seconds, String message) {
    try {
      Long count =
          redis.execute(
              SCRIPT, List.of(prefix + IdempotentCommand.hash(email)), Integer.toString(seconds));
      if (count != null && count > maximum)
        throw new DomainException(ErrorCode.RATE_LIMITED, message);
    } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
      throw new DomainException(
          ErrorCode.PROVIDER_UNAVAILABLE, "Sign-in rate limiting is unavailable.");
    }
  }
}
