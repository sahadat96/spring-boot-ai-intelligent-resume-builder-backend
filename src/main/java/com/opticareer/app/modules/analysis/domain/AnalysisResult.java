package com.opticareer.app.modules.analysis.domain;

import java.util.List;
import java.util.Map;

public record AnalysisResult(
    int overallScore,
    ScoreBreakdown atsBreakdown,
    ScoreBreakdown contentBreakdown,
    ScoreBreakdown formattingBreakdown,
    Integer jdMatchScore,
    List<String> strengths,
    List<String> areasToImprove,
    List<String> keywords,
    List<String> missingKeywords,
    Map<ResumeSection, String> sections,
    boolean ocrUsed,
    String parserVersion,
    long processingMs
) {
    public AnalysisResult(
        int overallScore,
        ScoreBreakdown atsBreakdown,
        ScoreBreakdown contentBreakdown,
        ScoreBreakdown formattingBreakdown,
        List<String> strengths,
        List<String> areasToImprove,
        List<String> keywords,
        Map<ResumeSection, String> sections,
        boolean ocrUsed,
        String parserVersion,
        long processingMs
    ) {
        this(
            overallScore,
            atsBreakdown,
            contentBreakdown,
            formattingBreakdown,
            null,
            strengths,
            areasToImprove,
            keywords,
            List.of(),
            sections,
            ocrUsed,
            parserVersion,
            processingMs);
    }

    public int atsScore() { return atsBreakdown.score(); }
    public int contentScore() { return contentBreakdown.score(); }
    public int formattingScore() { return formattingBreakdown.score(); }
}
