package com.opticareer.app.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Enterprise-grade environment post-processor for fintech applications.
 * Automatically loads .env files in local/development environments before Spring Boot
 * resolves configuration properties and datasource credentials, while ensuring
 * that true operating system environment variables always take precedence.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
  private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

  @Override
  public int getOrder() {
    // Run with high precedence so loaded .env variables are available for application.yml placeholder resolution
    return Ordered.HIGHEST_PRECEDENCE + 9;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Path envPath = findDotenvPath();
    if (envPath == null || !Files.isRegularFile(envPath)) {
      return;
    }

    Map<String, Object> properties = loadDotenv(envPath);
    if (!properties.isEmpty()) {
      MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
      if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
        environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
      } else {
        environment.getPropertySources().addLast(propertySource);
      }
      log.info("Loaded {} configuration properties from {}", properties.size(), envPath.toAbsolutePath().normalize());
    }
  }

  private Path findDotenvPath() {
    Path current = Path.of(".env");
    if (Files.exists(current)) {
      return current;
    }
    String userDir = System.getProperty("user.dir");
    if (userDir != null) {
      Path userDirPath = Path.of(userDir, ".env");
      if (Files.exists(userDirPath)) {
        return userDirPath;
      }
    }
    Path parent = Path.of("..", ".env");
    if (Files.exists(parent)) {
      return parent;
    }
    return null;
  }

  private Map<String, Object> loadDotenv(Path path) {
    Map<String, Object> properties = new LinkedHashMap<>();
    try {
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      for (String rawLine : lines) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (line.startsWith("export ")) {
          line = line.substring(7).trim();
        }
        int separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
          continue;
        }
        String key = line.substring(0, separatorIndex).trim();
        String value = line.substring(separatorIndex + 1).trim();

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
          value = value.substring(1, value.length() - 1)
              .replace("\\n", "\n")
              .replace("\\t", "\t")
              .replace("\\\"", "\"")
              .replace("\\\\", "\\");
        } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
          value = value.substring(1, value.length() - 1);
        } else {
          int commentIndex = value.indexOf(" #");
          if (commentIndex >= 0) {
            value = value.substring(0, commentIndex).trim();
          }
        }

        if (!key.isEmpty()) {
          properties.put(key, value);
        }
      }
    } catch (IOException e) {
      log.warn("Failed to read .env file at {}: {}", path, e.getMessage());
    }
    return properties;
  }
}
