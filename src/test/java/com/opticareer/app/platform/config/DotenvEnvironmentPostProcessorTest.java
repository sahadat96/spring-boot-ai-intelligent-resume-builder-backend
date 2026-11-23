package com.opticareer.app.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

class DotenvEnvironmentPostProcessorTest {

  @Test
  @DisplayName("Loads .env file and parses unquoted, quoted, and export lines correctly")
  void loadsAndParsesDotenvFile(@TempDir Path tempDir) throws Exception {
    Path envFile = tempDir.resolve(".env");
    String envContent = """
        # Database credentials
        DATABASE_USER=custom_user
        export DATABASE_PASSWORD="secret\\npassword"
        
        # Security tokens
        SINGLE_QUOTED='single_quoted_val'
        UNQUOTED_WITH_COMMENT=my_val # inline comment
        EMPTY_VALUE=
        """;
    Files.writeString(envFile, envContent);

    DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor() {
      // Point to test file
      @Override
      public void postProcessEnvironment(
          org.springframework.core.env.ConfigurableEnvironment environment,
          SpringApplication application) {
        try {
          var method = DotenvEnvironmentPostProcessor.class.getDeclaredMethod("loadDotenv", Path.class);
          method.setAccessible(true);
          @SuppressWarnings("unchecked")
          var properties = (java.util.Map<String, Object>) method.invoke(this, envFile);
          environment.getPropertySources().addLast(
              new org.springframework.core.env.MapPropertySource("dotenvProperties", properties));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    StandardEnvironment env = new StandardEnvironment();
    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("DATABASE_USER")).isEqualTo("custom_user");
    assertThat(env.getProperty("DATABASE_PASSWORD")).isEqualTo("secret\npassword");
    assertThat(env.getProperty("SINGLE_QUOTED")).isEqualTo("single_quoted_val");
    assertThat(env.getProperty("UNQUOTED_WITH_COMMENT")).isEqualTo("my_val");
  }

  @Test
  @DisplayName("Dotenv post-processor order is HIGHEST_PRECEDENCE + 9 to run before ConfigDataEnvironmentPostProcessor")
  void postProcessorOrder() {
    DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
    assertThat(processor.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 9);
  }
}
