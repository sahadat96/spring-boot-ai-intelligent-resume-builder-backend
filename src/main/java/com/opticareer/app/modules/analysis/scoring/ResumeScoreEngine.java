package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.*;
import com.opticareer.app.modules.analysis.parsing.TextAnalyzer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ResumeScoreEngine {

    private final AtsScoreCalculator atsCalculator;
    private final ContentScoreCalculator contentCalculator;
    private final FormattingScoreCalculator formattingCalculator;

    public ResumeScoreEngine(
            AtsScoreCalculator atsCalculator,
            ContentScoreCalculator contentCalculator,
            FormattingScoreCalculator formattingCalculator) {
        this.atsCalculator = atsCalculator;
        this.contentCalculator = contentCalculator;
        this.formattingCalculator = formattingCalculator;
    }

    public AnalysisResult analyze(ParsedDocument doc) {
        return analyze(doc, null, null);
    }

    public AnalysisResult analyze(ParsedDocument doc, String targetRole, String jobDescription) {
        long start = System.nanoTime();

        ScoreBreakdown atsBreakdown = atsCalculator.calculate(doc);
        ScoreBreakdown contentBreakdown = contentCalculator.calculate(doc);
        ScoreBreakdown formattingBreakdown = formattingCalculator.calculate(doc);

        Integer jdMatchScore = null;
        List<String> missingKeywords = List.of();
        boolean hasTargetContext = (targetRole != null && !targetRole.isBlank())
            || (jobDescription != null && !jobDescription.isBlank());

        if (hasTargetContext) {
            TextAnalyzer.JobMatchResult jdMatch = TextAnalyzer.matchJobDescription(doc.rawText(), targetRole, jobDescription);
            jdMatchScore = jdMatch.matchScore();
            missingKeywords = jdMatch.missingKeywords().stream().limit(12).toList();
        }

        double rawOverall = atsBreakdown.score() * 0.35
            + contentBreakdown.score() * 0.35
            + formattingBreakdown.score() * 0.30;

        if (jdMatchScore != null) {
            // Blend JD alignment lightly when explicit target role / JD is supplied
            rawOverall = rawOverall * 0.85 + jdMatchScore * 0.15;
        }

        int overallScore = Math.min(100, Math.max(0, (int) Math.round(rawOverall)));

        String domain = hasTargetContext
            ? DomainKeywordDictionary.detectDomain(
                (targetRole != null ? targetRole + " " : "") + (jobDescription != null ? jobDescription : ""))
            : DomainKeywordDictionary.detectDomain(doc.rawText());

        Set<String> domainKws = DomainKeywordDictionary.DOMAIN_KEYWORDS.getOrDefault(domain, Set.of());
        String skillsSection = doc.sections().getOrDefault(ResumeSection.SKILLS, "");
        List<String> keywords = TextAnalyzer.extractKeywords(doc.rawText(), skillsSection, domainKws, 15);

        List<String> strengths = detectStrengths(doc, atsBreakdown, contentBreakdown, formattingBreakdown, jdMatchScore);
        List<String> areasToImprove = detectAreasToImprove(atsBreakdown, contentBreakdown, formattingBreakdown, missingKeywords);

        long elapsed = Math.max(1L, (System.nanoTime() - start) / 1_000_000 + doc.parseTimeMs());

        return new AnalysisResult(
            overallScore,
            atsBreakdown,
            contentBreakdown,
            formattingBreakdown,
            jdMatchScore,
            strengths,
            areasToImprove,
            keywords,
            missingKeywords,
            doc.sections(),
            doc.ocrUsed(),
            doc.parserVersion(),
            elapsed
        );
    }

    private List<String> detectStrengths(
            ParsedDocument doc,
            ScoreBreakdown ats,
            ScoreBreakdown content,
            ScoreBreakdown formatting,
            Integer jdMatchScore) {
        List<String> strengths = new ArrayList<>();

        if (jdMatchScore != null && jdMatchScore >= 75) {
            strengths.add("Strong alignment (" + jdMatchScore + "%) with target role keywords");
        }

        for (ScoreDetail d : ats.details()) {
            if (d.passed() && d.pointsEarned() >= d.pointsMax() * 0.8) {
                switch (d.rule()) {
                    case "CONTACT_INFO" -> strengths.add("Complete contact header with email, phone, and profile link");
                    case "STANDARD_SECTIONS" -> strengths.add("Well-structured resume using ATS-recognized section headers");
                    case "NO_TABLES" -> strengths.add("Clean single-column layout without ATS table traps");
                    case "TEXT_SEARCHABLE" -> strengths.add("Machine-readable PDF text layer with standard ATS typography");
                    case "KEYWORD_DENSITY" -> strengths.add("Strong domain keyword presence for automated screening");
                    case "SKILLS_SECTION" -> strengths.add("Dedicated skills section for high-precision ATS indexing");
                    default -> {}
                }
            }
        }

        for (ScoreDetail d : content.details()) {
            if (d.passed() && d.pointsEarned() >= d.pointsMax() * 0.8) {
                switch (d.rule()) {
                    case "ACTION_VERBS" -> strengths.add("Effective use of varied, high-impact action verbs");
                    case "MEASURABLE_RESULTS" -> strengths.add("Good use of measurable achievements and quantified metrics");
                    case "PROFESSIONAL_SUMMARY" -> strengths.add("Well-crafted professional summary highlighting core value");
                    case "EDUCATION" -> strengths.add("Clear education credentials with degree and institution");
                    case "NO_PRONOUNS" -> strengths.add("Crisp, professional writing tone without first-person pronouns");
                    default -> {}
                }
            }
        }

        for (ScoreDetail d : formatting.details()) {
            if (d.passed() && d.pointsEarned() >= d.pointsMax() * 0.8) {
                switch (d.rule()) {
                    case "SECTION_ORDER" -> strengths.add("Easy-to-follow resume structure and logical section order");
                    case "EXPERIENCE_ENTRIES" -> strengths.add("Clear professional experience progression with timelines");
                    case "BULLET_CONSISTENCY" -> strengths.add("Consistent bullet point style and scannable length");
                    case "SKILLS_FORMAT" -> strengths.add("Skills formatted cleanly as a delimited list");
                    default -> {}
                }
            }
        }

        if (strengths.isEmpty()) {
            if (doc.wordCount() > 100) strengths.add("Resume contains substantive textual content");
            if (doc.bulletPoints().size() > 3) strengths.add("Uses bullet points to organize work history");
        }

        return strengths.size() > 6 ? strengths.subList(0, 6) : strengths;
    }

    private List<String> detectAreasToImprove(
            ScoreBreakdown ats,
            ScoreBreakdown content,
            ScoreBreakdown formatting,
            List<String> missingKeywords) {
        List<String> improvements = new ArrayList<>();

        if (missingKeywords != null && !missingKeywords.isEmpty()) {
            List<String> preview = missingKeywords.stream().limit(5).toList();
            improvements.add("Incorporate missing target keywords where accurate: " + String.join(", ", preview));
        }

        List<ScoreDetail> allDetails = new ArrayList<>();
        allDetails.addAll(ats.details());
        allDetails.addAll(content.details());
        allDetails.addAll(formatting.details());

        allDetails.stream()
            .filter(d -> !d.passed() || d.pointsEarned() < d.pointsMax() * 0.75)
            .sorted(Comparator.comparingInt((ScoreDetail d) -> d.pointsMax() - d.pointsEarned()).reversed())
            .limit(6)
            .forEach(d -> improvements.add(d.description()));

        return improvements.size() > 6 ? improvements.subList(0, 6) : improvements;
    }
}
