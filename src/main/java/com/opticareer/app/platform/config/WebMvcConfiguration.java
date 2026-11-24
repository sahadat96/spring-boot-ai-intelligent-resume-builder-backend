package com.opticareer.app.platform.config;

import com.opticareer.app.platform.idempotency.IdempotencyInterceptor;
import com.opticareer.app.platform.web.security.StepUpAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enterprise Web MVC configuration registering controller-level inspection interceptors.
 * <p>
 * Ensures that all /api/** endpoints pass through {@link IdempotencyInterceptor}
 * and {@link StepUpAuthInterceptor} before entering controller methods.
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  private final IdempotencyInterceptor idempotencyInterceptor;
  private final StepUpAuthInterceptor stepUpAuthInterceptor;

  public WebMvcConfiguration(
      IdempotencyInterceptor idempotencyInterceptor,
      StepUpAuthInterceptor stepUpAuthInterceptor) {
    this.idempotencyInterceptor = idempotencyInterceptor;
    this.stepUpAuthInterceptor = stepUpAuthInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(idempotencyInterceptor)
        .addPathPatterns("/api/**");

    registry
        .addInterceptor(stepUpAuthInterceptor)
        .addPathPatterns("/api/**");
  }
}
