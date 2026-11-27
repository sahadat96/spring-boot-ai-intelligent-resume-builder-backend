package com.opticareer.app.modules.analysis.infrastructure.persistence;

import com.opticareer.app.modules.analysis.domain.AiSuggestion;
import com.opticareer.app.platform.persistence.JpaAudit;
import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resume_analyses", schema = "analysis")
public class ResumeAnalysisEntity extends JpaAudit {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "target_role", length = 150)
    private String targetRole;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "ats_score")
    private Integer atsScore;

    @Column(name = "content_score")
    private Integer contentScore;

    @Column(name = "formatting_score")
    private Integer formattingScore;

    @Column(name = "jd_match_score")
    private Integer jdMatchScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", nullable = false, columnDefinition = "jsonb")
    private List<String> strengths = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "areas_to_improve", nullable = false, columnDefinition = "jsonb")
    private List<String> areasToImprove = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> keywords = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> missingKeywords = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> scoreDetails = Map.of();

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> sections = Map.of();

    @Column(name = "ocr_used", nullable = false)
    private boolean ocrUsed;

    @Column(name = "parser_version", nullable = false, length = 30)
    private String parserVersion = "2.0.0";

    @Column(name = "ai_enhanced", nullable = false)
    private boolean aiEnhanced;

    @Column(name = "ai_provider", length = 30)
    private String aiProvider;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_suggestions", columnDefinition = "jsonb")
    private AiSuggestion aiSuggestions;

    @Column(name = "processing_ms", nullable = false)
    private long processingMs;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }
    public Integer getAtsScore() { return atsScore; }
    public void setAtsScore(Integer atsScore) { this.atsScore = atsScore; }
    public Integer getContentScore() { return contentScore; }
    public void setContentScore(Integer contentScore) { this.contentScore = contentScore; }
    public Integer getFormattingScore() { return formattingScore; }
    public void setFormattingScore(Integer formattingScore) { this.formattingScore = formattingScore; }
    public Integer getJdMatchScore() { return jdMatchScore; }
    public void setJdMatchScore(Integer jdMatchScore) { this.jdMatchScore = jdMatchScore; }
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    public List<String> getAreasToImprove() { return areasToImprove; }
    public void setAreasToImprove(List<String> areasToImprove) { this.areasToImprove = areasToImprove; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public List<String> getMissingKeywords() { return missingKeywords; }
    public void setMissingKeywords(List<String> missingKeywords) { this.missingKeywords = missingKeywords; }
    public Map<String, Object> getScoreDetails() { return scoreDetails; }
    public void setScoreDetails(Map<String, Object> scoreDetails) { this.scoreDetails = scoreDetails; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public Map<String, String> getSections() { return sections; }
    public void setSections(Map<String, String> sections) { this.sections = sections; }
    public boolean isOcrUsed() { return ocrUsed; }
    public void setOcrUsed(boolean ocrUsed) { this.ocrUsed = ocrUsed; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public boolean isAiEnhanced() { return aiEnhanced; }
    public void setAiEnhanced(boolean aiEnhanced) { this.aiEnhanced = aiEnhanced; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public AiSuggestion getAiSuggestions() { return aiSuggestions; }
    public void setAiSuggestions(AiSuggestion aiSuggestions) { this.aiSuggestions = aiSuggestions; }
    public long getProcessingMs() { return processingMs; }
    public void setProcessingMs(long processingMs) { this.processingMs = processingMs; }
}
