package com.opticareer.app.modules.analysis.api;

import com.opticareer.app.modules.analysis.application.ResumeAnalysisService;
import com.opticareer.app.modules.analysis.application.ResumeAnalysisService.AnalysisView;
import com.opticareer.app.modules.analysis.application.ResumeAnalysisService.HistoryItem;
import com.opticareer.app.modules.analysis.application.ResumeAnalysisService.UploadOutput;
import com.opticareer.app.modules.analysis.domain.AiSuggestion;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Resume Analysis",
    description = """
        Asynchronous and synchronous ATS resume parsing, deterministic scoring, and premium AI suggestion endpoints.

        **Recommended Frontend Integration Flow (Queue-Based):**
        1. Upload the selected file via `POST /api/v1/resume-analysis/upload` $\\rightarrow$ receives `fileId` (`201 Created`).
        2. When the user clicks **Analyze Resume** (or **Analyze Again**), call `POST /api/v1/resume-analysis/{fileId}/analyze` $\\rightarrow$ returns `202 Accepted` with `id`, `status: "QUEUED"`, and a `Location` header.
        3. Poll `GET /api/v1/resume-analysis/{id}` every 500–1000ms until `status` is `COMPLETED` or `FAILED`.
        4. Call `DELETE /api/v1/resume-analysis/files/{fileId}` when the user clicks **Remove Resume**.
        """)
@RestController
@RequestMapping("/api/v1/resume-analysis")
public class ResumeAnalysisController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.oasis.opendocument.text",
        "application/rtf",
        "text/rtf",
        "image/png",
        "image/x-png",
        "image/jpeg",
        "image/jpg",
        "image/pjpeg",
        "image/tiff"
    );

    private final ResumeAnalysisService service;
    private final Json json;

    public ResumeAnalysisController(ResumeAnalysisService service) {
        this(service, null);
    }

    @Autowired
    public ResumeAnalysisController(ResumeAnalysisService service, Json json) {
        this.service = service;
        this.json = json;
    }

    // --- OpenAPI / Swagger DTOs ---

    @Schema(name = "UploadResumeResponse", description = "Staged resume upload metadata returned after uploading and magic-byte validation")
    public record UploadResumeResponse(
        @Schema(description = "Unique file identifier to pass into POST /api/v1/resume-analysis/{fileId}/analyze", example = "01937f4a-1234-7000-8000-000000000001")
        UUID fileId,
        @Schema(description = "Sanitized original filename", example = "Sahadat_Resume.pdf")
        String fileName,
        @Schema(description = "File size in bytes (max 10 MB)", example = "99532")
        long fileSize,
        @Schema(description = "Normalized MIME type", example = "application/pdf")
        String mimeType,
        @Schema(description = "SHA-256 checksum hex string", example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,
        @Schema(description = "Staging status", example = "UPLOADED", allowableValues = {"UPLOADED"})
        String status
    ) {}

    @Schema(name = "AnalyzeFileRequest", description = "Optional target role or job description to evaluate keyword alignment and skill gaps")
    public record AnalyzeFileRequest(
        @Schema(description = "Optional target job title", example = "Senior Java Backend Engineer", maxLength = 150)
        @Size(max = 150) String targetRole,

        @Schema(description = "Optional job description text for keyword matching and gap detection", example = "Seeking a Senior Java Engineer with Spring Boot, PostgreSQL, Redis, Docker, and Kubernetes.", maxLength = 15000)
        @Size(max = 15000) String jobDescription
    ) {}

    @Schema(name = "ScoreDetailDto", description = "Diagnostic score result for an individual ATS/content/formatting rule")
    public record ScoreDetailDto(
        @Schema(description = "Deterministic rule identifier", example = "ACTION_VERBS")
        String rule,
        @Schema(description = "Human-readable explanation with observed counts/diagnostics", example = "5 of 6 bullets open with varied, strong action verbs (5 unique)")
        String description,
        @Schema(description = "Points earned for this rule", example = "25")
        int pointsEarned,
        @Schema(description = "Maximum possible points for this rule", example = "25")
        int pointsMax,
        @Schema(description = "Whether the rule passed threshold", example = "true")
        boolean passed
    ) {}

    @Schema(name = "ScoreBreakdownDto", description = "Rule-by-rule breakdown for one scoring dimension (ATS, Content, or Formatting)")
    public record ScoreBreakdownDto(
        @Schema(description = "Scoring dimension name", example = "ATS Compatibility")
        String category,
        @Schema(description = "Normalized 0-100 score for this dimension", example = "88")
        int score,
        @ArraySchema(schema = @Schema(implementation = ScoreDetailDto.class))
        List<ScoreDetailDto> details
    ) {}

    @Schema(name = "ScoreDetailsDto", description = "Complete transparent rule breakdown and baseline algorithmic findings")
    public record ScoreDetailsDto(
        @Schema(description = "ATS compatibility rule breakdown (35% of overall score)")
        ScoreBreakdownDto ats,
        @Schema(description = "Content quality rule breakdown (35% of overall score)")
        ScoreBreakdownDto content,
        @Schema(description = "Formatting and structure rule breakdown (30% of overall score)")
        ScoreBreakdownDto formatting,
        @ArraySchema(schema = @Schema(description = "Deterministic rule-based strengths before optional AI enrichment"))
        List<String> baselineStrengths,
        @ArraySchema(schema = @Schema(description = "Deterministic rule-based improvement items before optional AI enrichment"))
        List<String> baselineImprovements
    ) {}

    @Schema(name = "BulletRewriteDto", description = "Concrete AI-suggested bullet point rewrite grounded in existing resume content")
    public record BulletRewriteDto(
        @Schema(description = "Original bullet text from the resume", example = "Built REST APIs for backend services")
        String original,
        @Schema(description = "Improved XYZ-formatted version without inventing metrics", example = "Architected Spring Boot REST APIs serving core backend workflows with [X]% lower p95 latency")
        String suggested,
        @Schema(description = "Why this rewrite improves ATS & recruiter clarity", example = "Replaces generic verb with architectural specificity and impact framing")
        String reason
    ) {}

    @Schema(name = "AiSuggestionDto", description = "AI-enhanced strengths, improvement suggestions, keyword gaps, and bullet rewrites (strictly populated for active subscribed/premium users when AI is enabled)")
    public record AiSuggestionDto(
        @Schema(description = "Industry-tailored strength highlights", example = "[\"Strong backend architecture experience using Spring Boot, PostgreSQL, and Redis\"]")
        List<String> enhancedStrengths,
        @Schema(description = "Targeted actionable improvements", example = "[\"Quantify throughput or latency impact on API endpoints in your current role\"]")
        List<String> enhancedImprovements,
        @Schema(description = "Semantic keyword gaps detected against target role/industry", example = "[\"Kubernetes\", \"CI/CD\", \"Observability\"]")
        List<String> keywordGaps,
        @ArraySchema(schema = @Schema(implementation = BulletRewriteDto.class))
        List<BulletRewriteDto> bulletRewrites
    ) {}

    @Schema(name = "ResumeAnalysisResponse", description = "Resume analysis status, deterministic ATS scores, keywords, strengths/improvements, and optional premium AI suggestions")
    public record ResumeAnalysisResponse(
        @Schema(description = "Unique analysis job/record ID", example = "01937f4b-5678-7000-8000-000000000002")
        UUID id,
        @Schema(description = "Associated uploaded file ID (use with POST /{fileId}/analyze to re-run analysis)", example = "01937f4a-1234-7000-8000-000000000001")
        UUID fileId,
        @Schema(description = "Uploaded file name", example = "Sahadat_Resume.pdf")
        String fileName,
        @Schema(description = "Optional target role specified at analysis time", example = "Senior Java Backend Engineer")
        String targetRole,
        @Schema(description = "Async processing status. Poll GET /api/v1/resume-analysis/{id} until COMPLETED or FAILED.", example = "COMPLETED", allowableValues = {"QUEUED", "PROCESSING", "COMPLETED", "FAILED"})
        String status,
        @Schema(description = "Failure reason if status=FAILED; otherwise null")
        String errorMessage,
        @Schema(description = "Weighted overall score (0-100); null while QUEUED/PROCESSING", example = "82")
        Integer overallScore,
        @Schema(description = "ATS parseability & compatibility score (0-100); null while QUEUED/PROCESSING", example = "88")
        Integer atsScore,
        @Schema(description = "Content quality, action verbs & quantified metrics score (0-100); null while QUEUED/PROCESSING", example = "80")
        Integer contentScore,
        @Schema(description = "Formatting, section order & layout consistency score (0-100); null while QUEUED/PROCESSING", example = "78")
        Integer formattingScore,
        @Schema(description = "Job description keyword alignment score (0-100) if targetRole or jobDescription was provided; otherwise null", example = "75")
        Integer jdMatchScore,
        @Schema(description = "Top detected strengths (AI-enriched for premium users if AI is enabled)", example = "[\"Complete contact header with email, phone, and profile link\", \"Effective use of varied, high-impact action verbs\"]")
        List<String> strengths,
        @Schema(description = "Prioritized improvement areas (AI-enriched for premium users if AI is enabled)", example = "[\"Add measurable results to more experience bullets\", \"Incorporate missing target keywords: Kubernetes, Kafka\"]")
        List<String> areasToImprove,
        @Schema(description = "Important canonical keywords detected from the resume", example = "[\"React\", \"Next.js\", \"TypeScript\", \"JavaScript\", \"Node.js\", \"REST API\"]")
        List<String> keywords,
        @Schema(description = "Missing target/industry keywords identified from JD alignment and AI gap analysis", example = "[\"Kubernetes\", \"Docker\", \"CI/CD\"]")
        List<String> missingKeywords,
        @Schema(description = "Rule-by-rule transparent scoring breakdown (empty object while QUEUED/PROCESSING)")
        ScoreDetailsDto scoreDetails,
        @Schema(description = "True if Tesseract OCR fallback was required (scanned PDF or image upload)", example = "false")
        boolean ocrUsed,
        @Schema(description = "Total processing time in milliseconds", example = "142")
        long processingMs,
        @Schema(description = "True only when user holds an active premium subscription and AI suggestions succeeded", example = "false")
        boolean aiEnhanced,
        @Schema(description = "LLM provider used for suggestions (e.g. OPENAI, GEMINI, OPENAI_COMPAT); null on free tier", example = "OPENAI")
        String aiProvider,
        @Schema(description = "LLM model used for suggestions (e.g. gpt-4o, gemini-2.0-flash); null on free tier", example = "gpt-4o")
        String aiModel,
        @Schema(description = "Rich post-analysis AI suggestions (null when free tier, QUEUED/PROCESSING, or AI disabled)")
        AiSuggestionDto aiSuggestions,
        @Schema(description = "UTC timestamp when analysis was requested")
        Instant createdAt
    ) {}

    @Schema(name = "AnalysisHistoryItemResponse", description = "Compact summary item for user's resume analysis history list")
    public record AnalysisHistoryItemResponse(
        @Schema(description = "Analysis ID", example = "01937f4b-5678-7000-8000-000000000002")
        UUID id,
        @Schema(description = "Uploaded file ID", example = "01937f4a-1234-7000-8000-000000000001")
        UUID fileId,
        @Schema(description = "Original file name", example = "Sahadat_Resume.pdf")
        String fileName,
        @Schema(description = "Optional target role", example = "Senior Java Backend Engineer")
        String targetRole,
        @Schema(description = "Current status", example = "COMPLETED", allowableValues = {"QUEUED", "PROCESSING", "COMPLETED", "FAILED"})
        String status,
        @Schema(description = "Overall score (0-100)", example = "82")
        Integer overallScore,
        @Schema(description = "ATS score (0-100)", example = "88")
        Integer atsScore,
        @Schema(description = "Content score (0-100)", example = "80")
        Integer contentScore,
        @Schema(description = "Formatting score (0-100)", example = "78")
        Integer formattingScore,
        @Schema(description = "Whether AI suggestions were generated", example = "false")
        boolean aiEnhanced,
        @Schema(description = "UTC creation timestamp")
        Instant createdAt
    ) {}

    // --- Endpoints ---

    @Operation(
        summary = "Step 1: Upload resume file",
        description = "Uploads a resume file (`multipart/form-data`, field `file`), validates size (<= 10 MB), MIME type, and binary magic-bytes signature, and stages it so the user can inspect the uploaded file card and click **Analyze Resume** (`POST /api/v1/resume-analysis/{fileId}/analyze`). Supports PDF, DOCX, DOC, ODT, RTF, PNG, JPEG, and TIFF.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Resume uploaded and staged successfully.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UploadResumeResponse.class))),
        @ApiResponse(responseCode = "400", description = "Empty file or unsupported format/signature.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required (missing/invalid Bearer JWT).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "413", description = "File exceeds 10 MB upload limit.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResumeResponse> upload(
            @Parameter(description = "Resume file (PDF, DOCX, DOC, ODT, RTF, PNG, JPEG, or TIFF; max 10 MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        ValidatedFile validated = validateFile(file);
        UploadOutput out = service.uploadResume(ownerId, validated.bytes(), validated.mimeType(), validated.fileName());
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(new UploadResumeResponse(
                out.fileId(), out.fileName(), out.fileSize(), out.mimeType(), out.sha256(), out.status()
            ));
    }

    @Operation(
        summary = "Step 2: Queue ATS analysis for uploaded resume",
        description = "Enqueues an asynchronous analysis job (`Redis` queue + background worker) for a previously uploaded `fileId` (also used for **Analyze Again**). Returns `202 Accepted` with `status: \"QUEUED\"` and `Location: /api/v1/resume-analysis/{id}`. Poll `GET /api/v1/resume-analysis/{id}` until `status` becomes `COMPLETED` or `FAILED`.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description = "Analysis job queued.",
            headers = @Header(name = "Location", description = "Polling URI for analysis status and results (/api/v1/resume-analysis/{id})"),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResumeAnalysisResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Uploaded file not found for this user.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{fileId}/analyze")
    public ResponseEntity<ResumeAnalysisResponse> analyzeUploaded(
            @Parameter(description = "Uploaded file UUID returned by POST /upload", required = true)
            @PathVariable UUID fileId,
            @Valid @RequestBody(required = false) AnalyzeFileRequest body,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        String targetRole = body != null ? body.targetRole() : null;
        String jd = body != null ? body.jobDescription() : null;

        AnalysisView view = service.enqueueAnalysis(ownerId, fileId, targetRole, jd);
        return ResponseEntity.accepted()
            .location(URI.create("/api/v1/resume-analysis/" + view.id()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(toResponse(view));
    }

    @Operation(
        summary = "One-step upload and analyze resume",
        description = "Combines upload and analysis in a single multipart request. By default (`async=true`), stages the file, enqueues the job in Redis, and returns `202 Accepted` (`status=QUEUED`) with a `Location` header for polling. Pass `async=false` to block and return `200 OK` immediately (recommended only for small text PDFs/testing).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Synchronous analysis completed (async=false).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResumeAnalysisResponse.class))),
        @ApiResponse(
            responseCode = "202",
            description = "Asynchronous analysis job queued (async=true).",
            headers = @Header(name = "Location", description = "Polling URI (/api/v1/resume-analysis/{id})"),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResumeAnalysisResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid file type, signature, or validation error.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "413", description = "File exceeds 10 MB upload limit.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeAnalysisResponse> analyzeDirect(
            @Parameter(description = "Resume file (PDF, DOCX, DOC, ODT, RTF, PNG, JPEG, or TIFF; max 10 MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional target job title", example = "Senior Java Backend Engineer")
            @RequestParam(value = "targetRole", required = false) @Size(max = 150) String targetRole,
            @Parameter(description = "Optional job description text to measure keyword coverage and gaps")
            @RequestParam(value = "jobDescription", required = false) @Size(max = 15000) String jobDescription,
            @Parameter(description = "Set true (default) to run in async queue (returns 202), or false to execute synchronously (returns 200)")
            @RequestParam(value = "async", defaultValue = "true") boolean async,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        ValidatedFile validated = validateFile(file);

        AnalysisView view = service.analyzeDirect(
            ownerId, validated.bytes(), validated.mimeType(), validated.fileName(),
            targetRole, jobDescription, async
        );

        ResumeAnalysisResponse body = toResponse(view);
        if (async) {
            return ResponseEntity.accepted()
                .location(URI.create("/api/v1/resume-analysis/" + view.id()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(body);
    }

    @Operation(
        summary = "Step 3: Poll analysis status and fetch results",
        description = "Returns current status (`QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`). Once `COMPLETED`, contains `overallScore`, `atsScore`, `contentScore`, `formattingScore`, `strengths`, `areasToImprove`, `keywords`, `missingKeywords`, rule-by-rule `scoreDetails`, and (for subscribed users) `aiSuggestions`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analysis status/result retrieved.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResumeAnalysisResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Analysis record not found for this user.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ResumeAnalysisResponse> getById(
            @Parameter(description = "Analysis record UUID", required = true)
            @PathVariable UUID id,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(toResponse(service.getAnalysis(ownerId, id)));
    }

    @Operation(
        summary = "List resume analysis history",
        description = "Lists previous resume analysis summaries for the authenticated user ordered newest-first.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analysis history retrieved.",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = AnalysisHistoryItemResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisHistoryItemResponse>> history(
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        List<AnalysisHistoryItemResponse> items = service.getHistory(ownerId).stream()
            .map(h -> new AnalysisHistoryItemResponse(
                h.id(), h.fileId(), h.fileName(), h.targetRole(), h.status(),
                h.overallScore(), h.atsScore(), h.contentScore(), h.formattingScore(),
                h.aiEnhanced(), h.createdAt()
            ))
            .toList();
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(items);
    }

    @Operation(
        summary = "Delete an analysis record",
        description = "Deletes a resume analysis record belonging to the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Analysis deleted."),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Analysis not found.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnalysis(
            @Parameter(description = "Analysis record UUID", required = true)
            @PathVariable UUID id,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        service.deleteAnalysis(ownerId, id);
    }

    @Operation(
        summary = "Remove uploaded resume file",
        description = "Deletes an uploaded resume file belonging to the authenticated user (corresponds to **Remove Resume** button). Existing completed analysis summaries remain in history with `fileId = null`.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Uploaded resume removed."),
        @ApiResponse(responseCode = "401", description = "Authentication required.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Uploaded file not found.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUpload(
            @Parameter(description = "Uploaded file UUID", required = true)
            @PathVariable UUID fileId,
            @Parameter(hidden = true) JwtAuthenticationToken token) {
        UUID ownerId = requireOwner(token);
        service.deleteUpload(ownerId, fileId);
    }

    // --- Mapping & Validation Helpers ---

    private ResumeAnalysisResponse toResponse(AnalysisView v) {
        AiSuggestionDto aiDto = null;
        if (v.aiSuggestions() != null) {
            AiSuggestion s = v.aiSuggestions();
            List<BulletRewriteDto> rewrites = s.bulletRewrites() != null
                ? s.bulletRewrites().stream()
                    .map(b -> new BulletRewriteDto(b.original(), b.suggested(), b.reason()))
                    .toList()
                : List.of();
            aiDto = new AiSuggestionDto(
                s.enhancedStrengths(), s.enhancedImprovements(), s.keywordGaps(), rewrites
            );
        }

        ScoreDetailsDto detailsDto = mapScoreDetails(v.scoreDetails());

        return new ResumeAnalysisResponse(
            v.id(),
            v.fileId(),
            v.fileName(),
            v.targetRole(),
            v.status(),
            v.errorMessage(),
            v.overallScore(),
            v.atsScore(),
            v.contentScore(),
            v.formattingScore(),
            v.jdMatchScore(),
            v.strengths(),
            v.areasToImprove(),
            v.keywords(),
            v.missingKeywords(),
            detailsDto,
            v.ocrUsed(),
            v.processingMs(),
            v.aiEnhanced(),
            v.aiProvider(),
            v.aiModel(),
            aiDto,
            v.createdAt()
        );
    }

    private ScoreDetailsDto mapScoreDetails(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (json != null) {
            try {
                return json.convert(raw, ScoreDetailsDto.class);
            } catch (Exception ignored) {
                // Fall back to safe structural extraction if partially shaped map is present
            }
        }
        return null;
    }

    private UUID requireOwner(JwtAuthenticationToken token) {
        if (token == null) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        return UUID.fromString(token.getName());
    }

    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw DomainException.invalid("File is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw DomainException.invalid("File size exceeds 10 MB limit.");
        }

        String rawContentType = file.getContentType();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume";
        String resolvedMime = resolveMimeType(rawContentType, fileName);

        if (!ALLOWED_TYPES.contains(resolvedMime)) {
            throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT,
                "Unsupported file type: " + rawContentType + ". Accepted: PDF, DOCX, DOC, ODT, RTF, PNG, JPEG, TIFF.");
        }
        try {
            return new ValidatedFile(file.getBytes(), resolvedMime, fileName);
        } catch (IOException e) {
            throw new DomainException(ErrorCode.ANALYSIS_FAILED, "Could not read uploaded file bytes.");
        }
    }

    private String resolveMimeType(String contentType, String fileName) {
        String normalizedType = contentType != null ? contentType.toLowerCase(Locale.ROOT).trim() : "";
        if (normalizedType.equals("text/rtf")) {
            return "application/rtf";
        }
        if (normalizedType.equals("image/x-png")) {
            return "image/png";
        }
        if (normalizedType.equals("image/pjpeg")) {
            return "image/jpeg";
        }
        if (normalizedType.equals("application/octet-stream") || normalizedType.isBlank()) {
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".pdf")) return "application/pdf";
            if (lowerName.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (lowerName.endsWith(".doc")) return "application/msword";
            if (lowerName.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
            if (lowerName.endsWith(".rtf")) return "application/rtf";
            if (lowerName.endsWith(".png")) return "image/png";
            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
            if (lowerName.endsWith(".tif") || lowerName.endsWith(".tiff")) return "image/tiff";
        }
        return normalizedType;
    }

    private record ValidatedFile(byte[] bytes, String mimeType, String fileName) {}
}
