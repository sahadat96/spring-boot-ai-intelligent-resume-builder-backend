package com.opticareer.app.modules.analysis.application;

import com.opticareer.app.modules.analysis.AnalysisApi;
import com.opticareer.app.modules.analysis.domain.AiSuggestion;
import com.opticareer.app.modules.analysis.domain.AnalysisStatus;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeAnalysisEntity;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeAnalysisJpaRepository;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeUploadEntity;
import com.opticareer.app.modules.analysis.infrastructure.persistence.ResumeUploadJpaRepository;
import com.opticareer.app.modules.analysis.parsing.DocumentParserService;
import com.opticareer.app.platform.privacy.DataParticipant;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import com.opticareer.app.shared.id.UuidV7;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeAnalysisService implements DataParticipant, AnalysisApi {

    private final DocumentParserService parser;
    private final ResumeAnalysisProcessor processor;
    private final ResumeUploadJpaRepository uploadRepo;
    private final ResumeAnalysisJpaRepository analysisRepo;
    private final AnalysisJobQueue jobQueue;

    public record UploadOutput(
        UUID fileId,
        String fileName,
        long fileSize,
        String mimeType,
        String sha256,
        String status
    ) {}

    public record AnalysisView(
        UUID id,
        UUID fileId,
        String fileName,
        String targetRole,
        String status,
        String errorMessage,
        Integer overallScore,
        Integer atsScore,
        Integer contentScore,
        Integer formattingScore,
        Integer jdMatchScore,
        List<String> strengths,
        List<String> areasToImprove,
        List<String> keywords,
        List<String> missingKeywords,
        Map<String, Object> scoreDetails,
        boolean ocrUsed,
        long processingMs,
        boolean aiEnhanced,
        String aiProvider,
        String aiModel,
        AiSuggestion aiSuggestions,
        java.time.Instant createdAt
    ) {}

    public record HistoryItem(
        UUID id,
        UUID fileId,
        String fileName,
        String targetRole,
        String status,
        Integer overallScore,
        Integer atsScore,
        Integer contentScore,
        Integer formattingScore,
        boolean aiEnhanced,
        java.time.Instant createdAt
    ) {}

    public ResumeAnalysisService(
            DocumentParserService parser,
            ResumeAnalysisProcessor processor,
            ResumeUploadJpaRepository uploadRepo,
            ResumeAnalysisJpaRepository analysisRepo,
            AnalysisJobQueue jobQueue) {
        this.parser = parser;
        this.processor = processor;
        this.uploadRepo = uploadRepo;
        this.analysisRepo = analysisRepo;
        this.jobQueue = jobQueue;
    }

    @Transactional
    public UploadOutput uploadResume(UUID ownerId, byte[] bytes, String mimeType, String fileName) {
        parser.validateMagicBytes(bytes, mimeType);
        String sha256 = sha256Hex(bytes);

        ResumeUploadEntity upload = new ResumeUploadEntity();
        upload.setId(UuidV7.next());
        upload.setOwnerId(ownerId);
        upload.setFileName(sanitizeFileName(fileName));
        upload.setMimeType(mimeType);
        upload.setFileSize(bytes.length);
        upload.setSha256(sha256);
        upload.setContentBytes(bytes);
        uploadRepo.save(upload);

        return new UploadOutput(
            upload.getId(),
            upload.getFileName(),
            upload.getFileSize(),
            upload.getMimeType(),
            sha256,
            "UPLOADED"
        );
    }

    @Transactional
    public AnalysisView enqueueAnalysis(UUID ownerId, UUID fileId, String targetRole, String jobDescription) {
        ResumeUploadEntity upload = uploadRepo.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> DomainException.missing("Uploaded resume file"));

        ResumeAnalysisEntity analysis = createQueuedEntity(
            ownerId, upload.getId(), upload.getFileName(), upload.getFileSize(), upload.getMimeType(), targetRole, jobDescription
        );
        analysisRepo.save(analysis);

        jobQueue.enqueue(new AnalysisJob(
            analysis.getId(), ownerId, upload.getId(), targetRole, jobDescription
        ));

        return toView(analysis);
    }

    public AnalysisView analyzeUploadedSync(UUID ownerId, UUID fileId, String targetRole, String jobDescription) {
        AnalysisView queued = enqueueRecordOnly(ownerId, fileId, targetRole, jobDescription);
        try {
            processor.process(new AnalysisJob(queued.id(), ownerId, fileId, targetRole, jobDescription));
            return getAnalysis(ownerId, queued.id());
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException(ErrorCode.ANALYSIS_FAILED, "Resume analysis failed: " + e.getMessage());
        }
    }

    public AnalysisView analyzeDirect(
            UUID ownerId,
            byte[] bytes,
            String mimeType,
            String fileName,
            String targetRole,
            String jobDescription,
            boolean async) {
        UploadOutput uploaded = uploadResume(ownerId, bytes, mimeType, fileName);
        if (async) {
            return enqueueAnalysis(ownerId, uploaded.fileId(), targetRole, jobDescription);
        }
        return analyzeUploadedSync(ownerId, uploaded.fileId(), targetRole, jobDescription);
    }

    @Transactional(readOnly = true)
    public AnalysisView getAnalysis(UUID ownerId, UUID id) {
        ResumeAnalysisEntity entity = analysisRepo.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> DomainException.missing("Analysis"));
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> getHistory(UUID ownerId) {
        return analysisRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
            .map(e -> new HistoryItem(
                e.getId(),
                e.getFileId(),
                e.getFileName(),
                e.getTargetRole(),
                e.getStatus(),
                e.getOverallScore(),
                e.getAtsScore(),
                e.getContentScore(),
                e.getFormattingScore(),
                e.isAiEnhanced(),
                e.createdAt
            ))
            .toList();
    }

    @Transactional
    public void deleteUpload(UUID ownerId, UUID fileId) {
        ResumeUploadEntity upload = uploadRepo.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> DomainException.missing("Uploaded resume file"));
        uploadRepo.delete(upload);
    }

    @Transactional
    public void deleteAnalysis(UUID ownerId, UUID id) {
        ResumeAnalysisEntity entity = analysisRepo.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> DomainException.missing("Analysis"));
        analysisRepo.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<CompletedAnalysisSnapshot> latestCompletedAnalysis(UUID ownerId) {
        return analysisRepo.findFirstByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, AnalysisStatus.COMPLETED.name())
            .map(e -> new CompletedAnalysisSnapshot(
                e.getId(),
                e.getFileId(),
                e.getFileName(),
                e.getTargetRole(),
                e.getOverallScore() != null ? e.getOverallScore() : 0,
                e.getAtsScore() != null ? e.getAtsScore() : 0,
                e.getContentScore() != null ? e.getContentScore() : 0,
                e.getFormattingScore() != null ? e.getFormattingScore() : 0,
                e.getJdMatchScore(),
                e.getStrengths(),
                e.getAreasToImprove(),
                e.getKeywords(),
                e.getMissingKeywords(),
                e.getScoreDetails(),
                e.getSections(),
                e.getExtractedText(),
                e.isOcrUsed(),
                e.isAiEnhanced(),
                e.updatedAt != null ? e.updatedAt : e.createdAt
            ));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<LatestStatus> latestAnalysisStatus(UUID ownerId) {
        return analysisRepo.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId)
            .map(e -> new LatestStatus(
                e.getId(),
                e.getFileId(),
                e.getFileName(),
                e.getStatus(),
                e.updatedAt != null ? e.updatedAt : e.createdAt
            ));
    }

    @Override
    @Transactional(readOnly = true)
    public long completedAnalysisCount(UUID ownerId) {
        return analysisRepo.countByOwnerIdAndStatus(ownerId, AnalysisStatus.COMPLETED.name());
    }

    @Override
    public String module() {
        return "analysis";
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportData(UUID owner) {
        return Map.of(
            "analyses", getHistory(owner),
            "uploadsCount", uploadRepo.findByOwnerIdOrderByCreatedAtDesc(owner).size()
        );
    }

    @Override
    @Transactional
    public void eraseData(UUID owner) {
        analysisRepo.deleteByOwnerId(owner);
        uploadRepo.deleteByOwnerId(owner);
    }

    private AnalysisView enqueueRecordOnly(UUID ownerId, UUID fileId, String targetRole, String jobDescription) {
        ResumeUploadEntity upload = uploadRepo.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> DomainException.missing("Uploaded resume file"));
        ResumeAnalysisEntity analysis = createQueuedEntity(
            ownerId, upload.getId(), upload.getFileName(), upload.getFileSize(), upload.getMimeType(), targetRole, jobDescription
        );
        analysisRepo.save(analysis);
        return toView(analysis);
    }

    private ResumeAnalysisEntity createQueuedEntity(
            UUID ownerId,
            UUID fileId,
            String fileName,
            long fileSize,
            String mimeType,
            String targetRole,
            String jobDescription) {
        ResumeAnalysisEntity entity = new ResumeAnalysisEntity();
        entity.setId(UuidV7.next());
        entity.setOwnerId(ownerId);
        entity.setFileId(fileId);
        entity.setFileName(fileName);
        entity.setFileSize(fileSize);
        entity.setMimeType(mimeType);
        entity.setTargetRole(targetRole != null && !targetRole.isBlank() ? targetRole.trim() : null);
        entity.setJobDescription(jobDescription != null && !jobDescription.isBlank() ? jobDescription.trim() : null);
        entity.setStatus(AnalysisStatus.QUEUED.name());
        return entity;
    }

    private AnalysisView toView(ResumeAnalysisEntity e) {
        return new AnalysisView(
            e.getId(),
            e.getFileId(),
            e.getFileName(),
            e.getTargetRole(),
            e.getStatus(),
            e.getErrorMessage(),
            e.getOverallScore(),
            e.getAtsScore(),
            e.getContentScore(),
            e.getFormattingScore(),
            e.getJdMatchScore(),
            e.getStrengths(),
            e.getAreasToImprove(),
            e.getKeywords(),
            e.getMissingKeywords(),
            e.getScoreDetails(),
            e.isOcrUsed(),
            e.getProcessingMs(),
            e.isAiEnhanced(),
            e.getAiProvider(),
            e.getAiModel(),
            e.getAiSuggestions(),
            e.createdAt
        );
    }

    private String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "resume";
        String clean = raw.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "SHA-256 unavailable.");
        }
    }
}
