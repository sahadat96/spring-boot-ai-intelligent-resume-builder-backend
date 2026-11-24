package com.opticareer.app.platform.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bank-grade Step-Up Authentication / Strong Customer Authentication (SCA) marker.
 * <p>
 * Modeled after European PSD2 RTS Article 4, this annotation requires that the authenticated
 * user has verified their master credentials or MFA within the specified maximum age.
 * Sensitive mutation operations (session revocation, credentials change, payment dispatch)
 * annotated with this will reject stale sessions with 403 Forbidden and WWW-Authenticate: StepUp.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRecentAuth {

  /**
   * Maximum allowed age in minutes since the last re-authentication.
   * Defaults to 15 minutes.
   */
  int maxAgeMinutes() default 15;
}
