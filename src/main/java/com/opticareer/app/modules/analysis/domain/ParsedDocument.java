package com.opticareer.app.modules.analysis.domain;

import java.util.List;
import java.util.Map;

public record ParsedDocument(
    String rawText,
    Map<ResumeSection, String> sections,
    List<String> bulletPoints,
    int pageCount,
    boolean ocrUsed,
    String parserVersion,
    long parseTimeMs,
    PdfLayoutMetadata layoutMetadata
) {
    public ParsedDocument(
        String rawText,
        Map<ResumeSection, String> sections,
        List<String> bulletPoints,
        int pageCount,
        boolean ocrUsed,
        String parserVersion,
        long parseTimeMs
    ) {
        this(rawText, sections, bulletPoints, pageCount, ocrUsed, parserVersion, parseTimeMs, PdfLayoutMetadata.empty());
    }

    public int wordCount() {
        if (rawText == null || rawText.isBlank()) return 0;
        return rawText.trim().split("\\s+").length;
    }

    public boolean hasSection(ResumeSection section) {
        return sections.containsKey(section) && !sections.get(section).isBlank();
    }
}
