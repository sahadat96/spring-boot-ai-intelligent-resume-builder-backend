package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.*;
import com.opticareer.app.modules.analysis.parsing.TextAnalyzer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ContentScoreCalculator {

    public ScoreBreakdown calculate(ParsedDocument doc) {
        List<ScoreDetail> details = new ArrayList<>();
        List<String> bullets = doc.bulletPoints();
        int totalBullets = bullets.size();

        // Rule 1: Action Verb Strength, Diversity & Zero Passive Fillers (25 pts)
        VerbAnalysis verbAnalysis = analyzeActionVerbs(bullets);
        if (totalBullets == 0) {
            details.add(ScoreDetail.partial("ACTION_VERBS", "No bullet points detected — structure experience using action-oriented bullets", 4, 25));
        } else {
            double ratio = (double) verbAnalysis.actionCount / totalBullets;
            double diversity = verbAnalysis.actionCount > 0
                ? (double) verbAnalysis.uniqueVerbs / verbAnalysis.actionCount
                : 0.0;

            int earned = (int) Math.round(Math.min(1.0, ratio / 0.65) * 20.0 + Math.min(1.0, diversity / 0.6) * 5.0);
            earned = Math.max(2, earned - verbAnalysis.weakStarterCount * 3);
            earned = Math.min(25, earned);

            if (ratio >= 0.6 && verbAnalysis.weakStarterCount == 0 && earned >= 22) {
                details.add(ScoreDetail.pass("ACTION_VERBS",
                    verbAnalysis.actionCount + " of " + totalBullets + " bullets open with varied, strong action verbs (" + verbAnalysis.uniqueVerbs + " unique)", 25));
            } else if (verbAnalysis.weakStarterCount > 0) {
                details.add(ScoreDetail.partial("ACTION_VERBS",
                    "Detected " + verbAnalysis.weakStarterCount + " passive starter(s) ('responsible for', 'worked on') — replace with impact verbs", earned, 25));
            } else {
                details.add(ScoreDetail.partial("ACTION_VERBS",
                    verbAnalysis.actionCount + " of " + totalBullets + " bullets start with strong action verbs — aim for 65%+", earned, 25));
            }
        }

        // Rule 2: Quantified XYZ/STAR Impact Metrics (25 pts)
        int metricBullets = TextAnalyzer.countMetricBullets(bullets);
        if (totalBullets == 0) {
            int rawMetrics = TextAnalyzer.countMetrics(doc.rawText());
            int pts = Math.min(15, rawMetrics * 3);
            details.add(ScoreDetail.partial("MEASURABLE_RESULTS", "No bullet points to evaluate — add quantified bullet accomplishments", Math.max(3, pts), 25));
        } else {
            double ratio = (double) metricBullets / totalBullets;
            if (ratio >= 0.45) {
                details.add(ScoreDetail.pass("MEASURABLE_RESULTS",
                    metricBullets + " of " + totalBullets + " bullets quantify outcomes with metrics (%, $, scale, latency)", 25));
            } else if (ratio >= 0.25) {
                details.add(ScoreDetail.partial("MEASURABLE_RESULTS",
                    metricBullets + " of " + totalBullets + " bullets include measurable results — aim for 45%+ quantified impact", (int) Math.round(ratio * 35) + 8, 25));
            } else {
                details.add(ScoreDetail.partial("MEASURABLE_RESULTS",
                    "Only " + metricBullets + " of " + totalBullets + " bullets contain numbers/metrics — apply the XYZ formula (Achieved X by Y% via Z)", (int) Math.round(ratio * 35) + 3, 25));
            }
        }

        // Rule 3: Professional Summary Quality (15 pts)
        String summary = doc.sections().getOrDefault(ResumeSection.SUMMARY, "");
        if (summary.isBlank()) {
            summary = doc.sections().getOrDefault(ResumeSection.OBJECTIVE, "");
        }
        int summaryWords = TextAnalyzer.countWords(summary);
        if (summaryWords >= 25 && summaryWords <= 85) {
            details.add(ScoreDetail.pass("PROFESSIONAL_SUMMARY", "Focused " + summaryWords + "-word professional summary highlights core value", 15));
        } else if (summaryWords >= 12) {
            details.add(ScoreDetail.partial("PROFESSIONAL_SUMMARY", "Summary is " + summaryWords + " words — ideal target is 25-85 words", 10, 15));
        } else if (summaryWords > 0) {
            details.add(ScoreDetail.partial("PROFESSIONAL_SUMMARY", "Summary is too brief (" + summaryWords + " words) — expand with key domain strengths", 5, 15));
        } else {
            details.add(ScoreDetail.fail("PROFESSIONAL_SUMMARY", "No professional summary section found — add a crisp 2-4 line summary at the top", 15));
        }

        // Rule 4: Ideal Word Count Density (15 pts)
        int wordCount = doc.wordCount();
        if (wordCount >= 320 && wordCount <= 850) {
            details.add(ScoreDetail.pass("WORD_COUNT", "Ideal resume depth (" + wordCount + " words)", 15));
        } else if (wordCount >= 220 && wordCount <= 1050) {
            details.add(ScoreDetail.partial("WORD_COUNT", wordCount + " words — optimal range for 1-2 pages is 320-850 words", 10, 15));
        } else if (wordCount < 220) {
            details.add(ScoreDetail.partial("WORD_COUNT", "Resume is sparse (" + wordCount + " words) — elaborate on technical impact and projects", 5, 15));
        } else {
            details.add(ScoreDetail.partial("WORD_COUNT", "Resume is dense (" + wordCount + " words) — trim older/less relevant items", 7, 15));
        }

        // Rule 5: Education Credentials (10 pts)
        String education = doc.sections().getOrDefault(ResumeSection.EDUCATION, "");
        boolean hasDegree = education.toLowerCase(Locale.ROOT)
            .matches("(?s).*(?:bachelor|master|phd|doctorate|mba|associate|b\\.?s\\.?|m\\.?s\\.?|b\\.?a\\.?|m\\.?a\\.?|b\\.?sc|m\\.?sc|b\\.?tech|m\\.?tech|b\\.?eng|m\\.?eng|diploma|certificate).*");
        boolean hasInstitution = !education.isBlank();
        if (hasDegree && hasInstitution) {
            details.add(ScoreDetail.pass("EDUCATION", "Education section clearly states degree and institution", 10));
        } else if (hasInstitution) {
            details.add(ScoreDetail.partial("EDUCATION", "Education section detected, but degree level is ambiguous", 6, 10));
        } else {
            details.add(ScoreDetail.fail("EDUCATION", "No Education section detected — add degree, university, and graduation year", 10));
        }

        // Rule 6: Professional Tone — Zero Pronouns & Cliches (10 pts)
        int pronounCount = TextAnalyzer.countPronouns(doc.rawText());
        int clicheCount = TextAnalyzer.countCliches(doc.rawText());
        if (pronounCount == 0 && clicheCount == 0) {
            details.add(ScoreDetail.pass("NO_PRONOUNS", "Crisp third-person resume voice free of pronouns and generic cliches", 10));
        } else if (pronounCount <= 2 && clicheCount <= 1) {
            details.add(ScoreDetail.partial("NO_PRONOUNS",
                "Minor style issues (" + pronounCount + " pronoun(s), " + clicheCount + " cliche(s)) — keep tone direct and evidence-based", 6, 10));
        } else {
            details.add(ScoreDetail.partial("NO_PRONOUNS",
                "Found " + pronounCount + " first-person pronouns and " + clicheCount + " vague cliche phrases — replace with concrete achievements", 2, 10));
        }

        return ScoreBreakdown.of("Content Quality", details);
    }

    private record VerbAnalysis(int actionCount, int uniqueVerbs, int weakStarterCount) {}

    private VerbAnalysis analyzeActionVerbs(List<String> bullets) {
        int actionCount = 0;
        Set<String> unique = new HashSet<>();
        for (String bullet : bullets) {
            String trimmed = bullet.trim();
            if (trimmed.isEmpty()) continue;
            String firstWord = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (ActionVerbDictionary.isActionVerb(firstWord)) {
                actionCount++;
                unique.add(firstWord);
            }
        }
        int weakCount = TextAnalyzer.countWeakStarterBullets(bullets);
        return new VerbAnalysis(actionCount, unique.size(), weakCount);
    }
}
