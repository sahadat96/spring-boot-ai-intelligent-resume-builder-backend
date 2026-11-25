package com.opticareer.app.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
 * <p>
 * Resolves configuration securely across environments in strict precedence order:
 * <ol>
 *   <li>True OS / RAM environment variables (highest priority)</li>
 *   <li>Docker Secrets mounted in RAM at {@code /run/secrets/*} (production VPS/Swarm/K8s)</li>
 *   <li>Local {@code .env} file (local development convenience only)</li>
 * </ol>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
  private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";
  private static final String DOCKER_SECRETS_SOURCE_NAME = "dockerSecretsProperties";
  private static final Path DEFAULT_SECRETS_DIR = Path.of("/run/secrets");

  private final Path secretsDir;

  public DotenvEnvironmentPostProcessor() {
    this(DEFAULT_SECRETS_DIR);
  }

  DotenvEnvironmentPostProcessor(Path secretsDir) {
    this.secretsDir = secretsDir;
  }

  @Override
  public int getOrder() {
    // Run with high precedence so secrets & .env variables are available for application.yml placeholders
    return Ordered.HIGHEST_PRECEDENCE + 9;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    // 1. Load local .env file first (lowest priority among external sources)
    Path envPath = findDotenvPath();
    if (envPath != null && Files.isRegularFile(envPath)) {
      Map<String, Object> properties = loadDotenv(envPath);
      if (!properties.isEmpty()) {
        addAfterSystemEnv(environment, new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        log.info("Loaded {} configuration properties from {}", properties.size(), envPath.toAbsolutePath().normalize());
      }
    }

    // 2. Load Docker Secrets (/run/secrets/*) so they override .env while still allowing OS env override
    if (secretsDir != null && Files.isDirectory(secretsDir)) {
      Map<String, Object> secrets = loadDockerSecrets(secretsDir);
      if (!secrets.isEmpty()) {
        addAfterSystemEnv(environment, new MapPropertySource(DOCKER_SECRETS_SOURCE_NAME, secrets));
        log.info("Loaded {} Docker secrets from {}", secrets.size(), secretsDir);
      }
    }
  }

  private void addAfterSystemEnv(ConfigurableEnvironment environment, MapPropertySource source) {
    if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
      environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
    } else {
      environment.getPropertySources().addFirst(source);
    }
  }

  Map<String, Object> loadDockerSecrets(Path dir) {
    Map<String, Object> secrets = new LinkedHashMap<>();
    try (Stream<Path> files = Files.list(dir)) {
      files
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                String name = file.getFileName().toString();
                if (name.startsWith(".") || !name.matches("^[A-Za-z0-9_.-]+$")) {
                  return;
                }
                try {
                  // Strip trailing newlines commonly added by editors/echo so Base64 keys never corrupt
                  String value = Files.readString(file, StandardCharsets.UTF_8).strip();
                  secrets.put(name, value);
                } catch (IOException e) {
                  log.warn("Failed to read Docker secret '{}': {}", name, e.getMessage());
                }
              });
    } catch (IOException e) {
      log.warn("Failed to scan Docker secrets directory {}: {}", dir, e.getMessage());
    }
    return secrets;
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
