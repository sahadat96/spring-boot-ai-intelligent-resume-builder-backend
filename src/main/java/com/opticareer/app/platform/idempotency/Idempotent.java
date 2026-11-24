package com.opticareer.app.platform.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enterprise banking marker annotation for idempotency enforcement.
 * <p>
 * When placed on a Controller method, {@code IdempotencyInterceptor} validates
 * the presence and syntactic safety of the {@code Idempotency-Key} HTTP header.
 * It prevents duplicate transaction executions and protects against network replay/double-click races.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

  /**
   * Logical domain scope for key isolation (e.g., "auth", "payment", "resume").
   */
  String scope() default "default";

  /**
   * Whether the Idempotency-Key header is mandatory for this endpoint.
   * If true and missing, a 400 Bad Request RFC 7807 problem detail is returned.
   */
  boolean required() default true;

  /**
   * Expiration window in hours for the stored idempotency fingerprint and lock.
   */
  int expiryHours() default 24;
}
