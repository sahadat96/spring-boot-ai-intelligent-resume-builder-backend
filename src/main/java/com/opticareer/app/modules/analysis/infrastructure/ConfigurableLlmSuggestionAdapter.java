package com.opticareer.app.modules.analysis.infrastructure;

import com.opticareer.app.modules.analysis.application.AiSuggestionPort;
import com.opticareer.app.modules.analysis.domain.AiSuggestion;
import com.opticareer.app.modules.analysis.domain.AnalysisResult;
import com.opticareer.app.modules.analysis.domain.ParsedDocument;
import com.opticareer.app.platform.config.AppProperties;
import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.idempotency.IdempotentCommand;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.shared.id.UuidV7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Provider-agnostic post-analysis LLM enhancer.
 * Supports switching seamlessly between OpenAI (e.g. gpt-4o / gpt-4o-mini) and Google Gemini
 * (native generateContent API or Gemini OpenAI-compatible endpoint) purely via configuration
 * (AI_URL, AI_MODEL, AI_API_KEY, AI_ENABLED).
 */
@Component
public class ConfigurableLlmSuggestionAdapter implements AiSuggestionPort {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableLlmSuggestionAdapter.class);
    private static final String CAPABILITY = "RESUME_ANALYSIS_SUGGESTIONS";
    private static final String PROMPT_VERSION = "v1";
    private static final int RESERVED_TOKENS_PER_CALL = 2500;

    private final AppProperties.Ai aiConfig;
    private final Json json;
    private final FieldCipher cipher;
    private final @Nullable JdbcTemplate jdbc;
    private final HttpClient httpClient;
    private final Semaphore concurrencyGate;

    public ConfigurableLlmSuggestionAdapter(
            AppProperties properties,
            Json json,
            FieldCipher cipher,
            @Nullable JdbcTemplate jdbc) {
        this.aiConfig = properties.ai();
        this.json = json;
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.concurrencyGate = new Semaphore(Math.max(1, aiConfig.maxConcurrent()));
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public EnhancedOutput enhance(
            UUID ownerId,
            UUID analysisId,
            ParsedDocument document,
            AnalysisResult result,
            String targetRole,
            String jobDescription) {

        if (!aiConfig.enabled() || aiConfig.apiKey() == null || aiConfig.apiKey().isBlank()) {
            return null;
        }
        if (withinDailyBudgetExceeded()) {
            log.warn("AI daily token limit ({}) reached; skipping AI suggestions for analysis {}",
                aiConfig.dailyTokenLimit(), analysisId);
            return null;
        }

        boolean acquired = false;
        long startedAt = System.currentTimeMillis();
        String provider = detectProvider(aiConfig.url(), aiConfig.model());
        String model = aiConfig.model();

        try {
            concurrencyGate.acquire();
            acquired = true;

            String prompt = buildPrompt(document, result, targetRole, jobDescription);
            LlmCallResult llmResult = "GEMINI".equals(provider)
                ? callGeminiNative(prompt)
                : callOpenAiCompatible(prompt);

            String cleanedJson = stripMarkdownFences(llmResult.content());
            AiSuggestion suggestion = json.read(cleanedJson, AiSuggestion.class);
            long latencyMs = System.currentTimeMillis() - startedAt;

            recordAuditUsage(
                ownerId, analysisId, prompt, model, "SUCCESS",
                llmResult.inputTokens(), llmResult.outputTokens(), latencyMs, cleanedJson, null
            );
            return new EnhancedOutput(suggestion, provider, model);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for AI concurrency permit on analysis {}", analysisId);
            return null;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startedAt;
            log.warn("AI suggestion failed for analysis {} (provider={}, model={}): {}",
                analysisId, provider, model, e.getMessage());
            recordAuditUsage(
                ownerId, analysisId, "", model, "FAILED",
                0L, 0L, latencyMs, null, "AI_SUGGESTION_ERROR"
            );
            return null;
        } finally {
            if (acquired) {
                concurrencyGate.release();
            }
        }
    }

    String detectProvider(String url, String model) {
        String u = url != null ? url.toLowerCase(Locale.ROOT) : "";
        String m = model != null ? model.toLowerCase(Locale.ROOT) : "";
        // Gemini OpenAI-compatible URL uses /openai suffix, so route that through OpenAI format;
        // native Gemini REST URL uses Gemini native format.
        if (u.contains("generativelanguage.googleapis.com") && !u.contains("/openai")) {
            return "GEMINI";
        }
        if (m.startsWith("gemini-") && !u.contains("openai")) {
            return u.contains("generativelanguage.googleapis.com") ? "GEMINI" : "OPENAI_COMPAT";
        }
        return u.contains("openai.com") ? "OPENAI" : "OPENAI_COMPAT";
    }

    private LlmCallResult callOpenAiCompatible(String prompt) throws Exception {
        String baseUrl = aiConfig.url().replaceAll("/+$", "");
        String endpoint = baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiConfig.model());
        body.put("messages", List.of(
            Map.of(
                "role", "system",
                "content", "You are an expert ATS resume strategist. Enhance post-analysis suggestions accurately without altering facts or inventing experience. Always output strict JSON only."
            ),
            Map.of("role", "user", "content", prompt)
        ));
        body.put("response_format", Map.of("type", "json_object"));

        if (isReasoningModel(aiConfig.model())) {
            body.put("max_completion_tokens", 1800);
        } else {
            body.put("temperature", 0.3);
            body.put("max_tokens", 1800);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + aiConfig.apiKey().trim())
            .timeout(Duration.ofSeconds(aiConfig.timeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(json.write(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("LLM endpoint returned HTTP " + response.statusCode());
        }

        JsonNode root = json.read(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("LLM endpoint returned empty choice content.");
        }
        long inputTokens = root.path("usage").path("prompt_tokens").asLong(0L);
        long outputTokens = root.path("usage").path("completion_tokens").asLong(0L);
        return new LlmCallResult(content, inputTokens, outputTokens);
    }

    private LlmCallResult callGeminiNative(String prompt) throws Exception {
        String baseUrl = aiConfig.url().replaceAll("/+$", "");
        String endpoint = baseUrl + "/models/" + aiConfig.model() + ":generateContent";

        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of(
                "parts", List.of(Map.of(
                    "text",
                    "You are an expert ATS resume strategist. Enhance post-analysis suggestions accurately without inventing experience. Always output strict JSON only."
                ))
            ),
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            ),
            "generationConfig", Map.of(
                "temperature", 0.3,
                "maxOutputTokens", 1800,
                "responseMimeType", "application/json"
            )
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", aiConfig.apiKey().trim())
            .timeout(Duration.ofSeconds(aiConfig.timeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(json.write(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini API returned HTTP " + response.statusCode());
        }

        JsonNode root = json.read(response.body());
        String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Gemini API returned empty candidate content.");
        }
        long inputTokens = root.path("usageMetadata").path("promptTokenCount").asLong(0L);
        long outputTokens = root.path("usageMetadata").path("candidatesTokenCount").asLong(0L);
        return new LlmCallResult(content, inputTokens, outputTokens);
    }

    private String buildPrompt(
            ParsedDocument document,
            AnalysisResult result,
            String targetRole,
            String jobDescription) {
        String resumeText = document.rawText();
        if (resumeText.length() > 3500) {
            resumeText = resumeText.substring(0, 3500) + "...";
        }
        String targetBlock = (targetRole != null && !targetRole.isBlank() ? "Target Role: " + targetRole + "\n" : "")
            + (jobDescription != null && !jobDescription.isBlank()
                ? "Target Job Description:\n" + jobDescription.substring(0, Math.min(jobDescription.length(), 1500)) + "\n"
                : "");

        return """
            You are enhancing an already-scored ATS resume report for a subscribed user.
            Do NOT re-score the resume. Focus strictly on high-value Strengths, Areas to Improve, Keywords/Keyword Gaps, and concrete Bullet Rewrites grounded strictly in facts already present in the resume.

            %sRESUME TEXT:
            %s

            ALGORITHMIC SCORES:
            - Overall: %d/100 | ATS: %d/100 | Content: %d/100 | Formatting: %d/100

            DETECTED KEYWORDS: %s
            MISSING TARGET KEYWORDS: %s
            BASELINE STRENGTHS:
            %s

            BASELINE AREAS TO IMPROVE:
            %s

            Respond strictly in valid JSON with this exact schema:
            {
              "enhancedStrengths": ["specific strength referencing actual resume evidence"],
              "enhancedImprovements": ["specific actionable improvement referencing exact section/gap"],
              "keywordGaps": ["relevant ATS keyword missing or under-emphasized for this candidate's field"],
              "bulletRewrites": [
                {
                  "original": "exact bullet copied from the resume",
                  "suggested": "rewritten version using strong action verb + [placeholder metric if none] without fabricating facts",
                  "reason": "brief explanation of ATS/impact improvement"
                }
              ]
            }

            Provide 4 enhancedStrengths, 4 enhancedImprovements, 6-10 keywordGaps, and 2-3 bulletRewrites.
            """.formatted(
                targetBlock,
                resumeText,
                result.overallScore(),
                result.atsScore(),
                result.contentScore(),
                result.formattingScore(),
                String.join(", ", result.keywords()),
                String.join(", ", result.missingKeywords()),
                String.join("\n", result.strengths()),
                String.join("\n", result.areasToImprove())
            );
    }

    private boolean isReasoningModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase(Locale.ROOT);
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("gpt-5");
    }

    private String stripMarkdownFences(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private boolean withinDailyBudgetExceeded() {
        if (jdbc == null) return false;
        try {
            Long usedToday = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(input_tokens + output_tokens), 0)
                FROM ai.ai_usage_attempts
                WHERE requested_day = ?
                """,
                Long.class,
                LocalDate.now(ZoneOffset.UTC)
            );
            return usedToday != null && usedToday >= aiConfig.dailyTokenLimit();
        } catch (Exception e) {
            return false;
        }
    }

    private void recordAuditUsage(
            UUID ownerId,
            UUID referenceId,
            String prompt,
            String model,
            String status,
            long inputTokens,
            long outputTokens,
            long latencyMs,
            String rawOutput,
            String errorCode) {
        if (jdbc == null) return;
        try {
            UUID requestId = UuidV7.next();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String requestHash = IdempotentCommand.hash(ownerId + ":" + referenceId + ":" + model);
            String encryptedOut = rawOutput != null ? cipher.encrypt(rawOutput, "ai:" + requestId) : null;

            jdbc.update(
                """
                INSERT INTO ai.ai_requests
                  (id, owner_id, reference_id, capability, request_hash, prompt_version, model, status,
                   requested_day, reserved_tokens, input_tokens, output_tokens, latency_ms, output_enc, error_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, reference_id, capability)
                DO UPDATE SET
                  status = EXCLUDED.status,
                  model = EXCLUDED.model,
                  input_tokens = EXCLUDED.input_tokens,
                  output_tokens = EXCLUDED.output_tokens,
                  latency_ms = EXCLUDED.latency_ms,
                  output_enc = EXCLUDED.output_enc,
                  error_code = EXCLUDED.error_code,
                  updated_at = now()
                """,
                requestId, ownerId, referenceId, CAPABILITY, requestHash, PROMPT_VERSION, model, status,
                today, RESERVED_TOKENS_PER_CALL, inputTokens, outputTokens, latencyMs, encryptedOut, errorCode
            );

            UUID parentId = jdbc.queryForObject(
                "SELECT id FROM ai.ai_requests WHERE owner_id = ? AND reference_id = ? AND capability = ?",
                UUID.class, ownerId, referenceId, CAPABILITY
            );

            jdbc.update(
                """
                INSERT INTO ai.ai_usage_attempts
                  (id, request_id, owner_id, requested_day, input_tokens, output_tokens, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                """,
                UuidV7.next(), parentId != null ? parentId : requestId, ownerId, today, inputTokens, outputTokens
            );
        } catch (Exception e) {
            log.debug("Could not persist AI audit usage: {}", e.getMessage());
        }
    }

    private record LlmCallResult(String content, long inputTokens, long outputTokens) {}
}
