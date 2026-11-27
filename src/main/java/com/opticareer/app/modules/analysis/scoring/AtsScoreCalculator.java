package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.*;
import com.opticareer.app.modules.analysis.parsing.TextAnalyzer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AtsScoreCalculator {

    private static final Set<String> ATS_SAFE_FONTS = Set.of(
        "arial", "calibri", "cambria", "georgia", "helvetica", "garamond",
        "times", "verdana", "tahoma", "trebuchet", "lato", "roboto",
        "inter", "opensans", "open sans", "sourcesans", "source sans", "carlito", "liberation"
    );

    public ScoreBreakdown calculate(ParsedDocument doc) {
        List<ScoreDetail> details = new ArrayList<>();
        PdfLayoutMetadata layout = doc.layoutMetadata() != null ? doc.layoutMetadata() : PdfLayoutMetadata.empty();

        // Rule 1: Contact Information + Profile Links (10 pts)
        boolean hasEmail = TextAnalyzer.hasEmail(doc.rawText());
        boolean hasPhone = TextAnalyzer.hasPhone(doc.rawText());
        boolean hasProfileUrl = TextAnalyzer.hasUrl(doc.rawText(), layout.annotationUrls());
        if (hasEmail && hasPhone && hasProfileUrl) {
            details.add(ScoreDetail.pass("CONTACT_INFO", "Resume includes email, phone number, and professional profile/portfolio link", 10));
        } else if (hasEmail && hasPhone) {
            details.add(ScoreDetail.partial("CONTACT_INFO", "Includes email and phone; consider adding a LinkedIn or portfolio URL", 8, 10));
        } else if (hasEmail || hasPhone) {
            details.add(ScoreDetail.partial("CONTACT_INFO", "Includes " + (hasEmail ? "email" : "phone") + " but missing " + (hasEmail ? "phone" : "email"), 5, 10));
        } else {
            details.add(ScoreDetail.fail("CONTACT_INFO", "No email or phone number detected", 10));
        }

        // Rule 2: Standard Sections (15 pts)
        Set<ResumeSection> expectedSections = Set.of(
            ResumeSection.EXPERIENCE, ResumeSection.EDUCATION, ResumeSection.SKILLS, ResumeSection.SUMMARY
        );
        int foundSections = 0;
        for (ResumeSection s : expectedSections) {
            if (doc.hasSection(s)) foundSections++;
        }
        if (foundSections >= 3) {
            details.add(ScoreDetail.pass("STANDARD_SECTIONS", foundSections + " of 4 core sections detected (Experience, Education, Skills, Summary)", 15));
        } else if (foundSections == 2) {
            details.add(ScoreDetail.partial("STANDARD_SECTIONS", "Only 2 of 4 core sections detected — add missing standard headers", 8, 15));
        } else {
            details.add(ScoreDetail.partial("STANDARD_SECTIONS", "Only " + foundSections + " standard section(s) detected — ATS may fail to categorize content", foundSections * 4, 15));
        }

        // Rule 3: Single-Column & Table-Free Layout (10 pts) — uses PDFBox coordinate physics + whitespace fallback
        boolean multiColOrTable = layout.hasMultiColumnLayout() || detectTableFormatting(doc.rawText());
        if (!multiColOrTable) {
            details.add(ScoreDetail.pass("NO_TABLES", "Clean single-column layout without complex tables — optimal for ATS parsers", 10));
        } else {
            details.add(ScoreDetail.fail("NO_TABLES", "Multi-column or table layout detected — legacy ATS parsers may scramble reading order", 10));
        }

        // Rule 4: Text Layer & Font Compatibility (10 pts)
        if (!doc.ocrUsed() && layout.garbledCharRatio() <= 0.05) {
            boolean standardFont = hasStandardAtsFont(layout.fontNames());
            if (standardFont) {
                details.add(ScoreDetail.pass("TEXT_SEARCHABLE", "Embedded machine-readable text using ATS-standard fonts", 10));
            } else {
                details.add(ScoreDetail.partial("TEXT_SEARCHABLE", "Document is text-searchable, though custom fonts were detected (prefer Arial/Calibri/Inter)", 8, 10));
            }
        } else {
            details.add(ScoreDetail.partial("TEXT_SEARCHABLE", "Text required OCR or had encoding artifacts — export directly as a text-based PDF/DOCX", 4, 10));
        }

        // Rule 5: Explicit Skills Section (10 pts)
        if (doc.hasSection(ResumeSection.SKILLS)) {
            details.add(ScoreDetail.pass("SKILLS_SECTION", "Dedicated Skills section present for exact-match ATS indexing", 10));
        } else {
            details.add(ScoreDetail.fail("SKILLS_SECTION", "No dedicated Skills section found — add an explicit 'Skills' header", 10));
        }

        // Rule 6: ATS-Safe Bullet Symbols & No Heavy Image Traps (5 pts)
        boolean tooManyImages = layout.embeddedImageCount() >= 3;
        if (!TextAnalyzer.hasUnicodeBullets(doc.rawText()) && !tooManyImages) {
            details.add(ScoreDetail.pass("CLEAN_BULLETS", "ATS-safe characters and no image-heavy graphics blocking text", 5));
        } else if (tooManyImages) {
            details.add(ScoreDetail.partial("CLEAN_BULLETS", layout.embeddedImageCount() + " embedded images found — avoid skill bars/headshots/icons for ATS safety", 2, 5));
        } else {
            details.add(ScoreDetail.partial("CLEAN_BULLETS", "Decorative Unicode bullets detected — standard hyphens (-) or circles parse more cleanly", 3, 5));
        }

        // Rule 7: Parseable Employment Dates (10 pts)
        int dateCount = TextAnalyzer.countDates(doc.rawText());
        if (dateCount >= 4) {
            details.add(ScoreDetail.pass("PARSEABLE_DATES", dateCount + " parseable dates found — ATS can reconstruct timeline and tenure", 10));
        } else if (dateCount >= 2) {
            details.add(ScoreDetail.partial("PARSEABLE_DATES", dateCount + " date references found — ensure all roles & education include start/end dates", 6, 10));
        } else {
            details.add(ScoreDetail.partial("PARSEABLE_DATES", "Few parseable dates found — format dates consistently as 'Mon YYYY - Mon YYYY'", dateCount * 3, 10));
        }

        // Rule 8: Content Length (5 pts)
        int wordCount = doc.wordCount();
        if (wordCount >= 250 && wordCount <= 1100) {
            details.add(ScoreDetail.pass("CONTENT_LENGTH", wordCount + " words — solid depth for ATS keyword indexing", 5));
        } else if (wordCount < 250) {
            details.add(ScoreDetail.partial("CONTENT_LENGTH", "Only " + wordCount + " words — too sparse for strong ATS ranking", 2, 5));
        } else {
            details.add(ScoreDetail.partial("CONTENT_LENGTH", wordCount + " words — consider tightening to 1-2 focused pages", 3, 5));
        }

        // Rule 9: No Repetitive Headers/Footers (5 pts)
        if (!detectRepeatedHeaders(doc.rawText())) {
            details.add(ScoreDetail.pass("NO_HEADERS_FOOTERS", "No repeating header/footer noise detected across pages", 5));
        } else {
            details.add(ScoreDetail.fail("NO_HEADERS_FOOTERS", "Repeating header/footer text detected — move contact info into the main body", 5));
        }

        // Rule 10: Domain Keyword Coverage (20 pts)
        String domain = DomainKeywordDictionary.detectDomain(doc.rawText());
        Set<String> domainKws = DomainKeywordDictionary.DOMAIN_KEYWORDS.getOrDefault(domain, Set.of());
        List<String> extracted = TextAnalyzer.extractKeywords(doc.rawText(), domainKws, 30);
        long domainMatches = extracted.stream()
            .map(DomainKeywordDictionary::canonicalize)
            .filter(domainKws::contains)
            .count();

        if (domainMatches >= 10) {
            details.add(ScoreDetail.pass("KEYWORD_DENSITY", domainMatches + " verified " + domain.toLowerCase().replace("_", " ") + " skills matched", 20));
        } else if (domainMatches >= 5) {
            details.add(ScoreDetail.partial("KEYWORD_DENSITY", domainMatches + " domain skills matched — target 10+ role-specific skills", (int) (domainMatches * 2), 20));
        } else {
            details.add(ScoreDetail.partial("KEYWORD_DENSITY", "Only " + domainMatches + " domain keywords detected — add core technical/domain terminology", (int) (domainMatches * 2), 20));
        }

        return ScoreBreakdown.of("ATS Compatibility", details);
    }

    private boolean hasStandardAtsFont(Set<String> fontNames) {
        if (fontNames == null || fontNames.isEmpty()) return true;
        for (String name : fontNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String safe : ATS_SAFE_FONTS) {
                if (lower.contains(safe)) return true;
            }
        }
        return false;
    }

    private boolean detectTableFormatting(String text) {
        int tableLines = 0;
        for (String line : text.split("\n")) {
            String[] segments = line.split("\\s{4,}");
            if (segments.length >= 3 && line.trim().length() > 25) {
                tableLines++;
            }
        }
        return tableLines >= 4;
    }

    private boolean detectRepeatedHeaders(String text) {
        String[] lines = text.split("\n");
        if (lines.length < 25) return false;
        Map<String, Integer> lineCounts = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 8 && trimmed.length() < 55) {
                lineCounts.merge(trimmed, 1, Integer::sum);
            }
        }
        return lineCounts.values().stream().anyMatch(c -> c >= 3);
    }
}
