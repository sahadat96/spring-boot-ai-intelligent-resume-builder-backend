package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.*;
import com.opticareer.app.modules.analysis.parsing.TextAnalyzer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FormattingScoreCalculator {

    private static final List<ResumeSection> PREFERRED_ORDER = List.of(
        ResumeSection.CONTACT, ResumeSection.SUMMARY, ResumeSection.EXPERIENCE,
        ResumeSection.PROJECTS, ResumeSection.EDUCATION, ResumeSection.SKILLS,
        ResumeSection.CERTIFICATIONS
    );

    public ScoreBreakdown calculate(ParsedDocument doc) {
        List<ScoreDetail> details = new ArrayList<>();
        PdfLayoutMetadata layout = doc.layoutMetadata() != null ? doc.layoutMetadata() : PdfLayoutMetadata.empty();

        // Rule 1: ATS-Recommended Section Sequence (20 pts)
        int orderScore = calculateSectionOrderScore(doc.sections());
        if (orderScore >= 80) {
            details.add(ScoreDetail.pass("SECTION_ORDER", "Logical ATS section flow (Contact → Summary → Experience → Education → Skills)", 20));
        } else if (orderScore >= 50) {
            details.add(ScoreDetail.partial("SECTION_ORDER", "Section order is acceptable; placing Summary & Experience near top improves scan speed", (int) (20 * orderScore / 100.0), 20));
        } else {
            details.add(ScoreDetail.partial("SECTION_ORDER", "Non-standard section sequence — reorganize to Summary, Experience, Education, Skills", (int) (20 * orderScore / 100.0), 20));
        }

        // Rule 2: Bullet Consistency & Bullet Word Length (15 pts)
        boolean consistent = TextAnalyzer.hasConsistentBullets(doc.bulletPoints(), doc.rawText());
        int idealLengthBullets = countIdealLengthBullets(doc.bulletPoints());
        if (doc.bulletPoints().isEmpty()) {
            details.add(ScoreDetail.partial("BULLET_CONSISTENCY", "No bullet points detected — break dense blocks into 1-2 line bullet points", 3, 15));
        } else {
            double idealRatio = (double) idealLengthBullets / doc.bulletPoints().size();
            if (consistent && idealRatio >= 0.6) {
                details.add(ScoreDetail.pass("BULLET_CONSISTENCY", "Consistent bullet markers and concise bullet length (8-28 words)", 15));
            } else if (consistent) {
                details.add(ScoreDetail.partial("BULLET_CONSISTENCY", "Bullet styles match, but several bullets are overly long or fragmented (aim for 8-28 words)", 10, 15));
            } else {
                details.add(ScoreDetail.partial("BULLET_CONSISTENCY", "Mixed bullet marker symbols detected — standardize to one style throughout", 7, 15));
            }
        }

        // Rule 3: Line Length & Typography Hierarchy (10 pts)
        int maxLine = TextAnalyzer.longestLineLength(doc.rawText());
        boolean hasFontHierarchy = layout.bodyFontSizePt() == 0f || layout.hasHeadingSizeHierarchy();
        if (maxLine <= 115 && hasFontHierarchy) {
            details.add(ScoreDetail.pass("LINE_LENGTH", "Well-proportioned line lengths and clear heading typography hierarchy", 10));
        } else if (maxLine <= 140) {
            details.add(ScoreDetail.partial("LINE_LENGTH", "Some lines exceed 115 chars or lack visual heading contrast", 6, 10));
        } else {
            details.add(ScoreDetail.partial("LINE_LENGTH", "Long unwrapped lines detected (max " + maxLine + " chars) — shorten lines for readability", 3, 10));
        }

        // Rule 4: Concise Sentence Readability (10 pts)
        double avgSentence = TextAnalyzer.averageSentenceLength(doc.rawText());
        if (avgSentence >= 7 && avgSentence <= 24) {
            details.add(ScoreDetail.pass("SENTENCE_LENGTH", "Strong readability (" + String.format("%.0f", avgSentence) + " words average per phrase)", 10));
        } else if (avgSentence >= 5 && avgSentence <= 35) {
            details.add(ScoreDetail.partial("SENTENCE_LENGTH", "Average phrase length is " + String.format("%.0f", avgSentence) + " words — aim for 8-24 words", 6, 10));
        } else {
            details.add(ScoreDetail.partial("SENTENCE_LENGTH", "Average phrase length (" + String.format("%.0f", avgSentence) + " words) hurts scannability", 3, 10));
        }

        // Rule 5: Experience Progression & Structure (20 pts)
        int experienceEntries = countExperienceEntries(doc);
        int projectEntries = doc.hasSection(ResumeSection.PROJECTS) ? 1 : 0;
        if (experienceEntries >= 3 || (experienceEntries >= 2 && projectEntries >= 1)) {
            details.add(ScoreDetail.pass("EXPERIENCE_ENTRIES", experienceEntries + " experience role(s) structured cleanly with timelines", 20));
        } else if (experienceEntries == 2 || (experienceEntries == 1 && projectEntries >= 1)) {
            details.add(ScoreDetail.partial("EXPERIENCE_ENTRIES", experienceEntries + " experience entry/entries found — strong foundation", 15, 20));
        } else if (experienceEntries == 1) {
            details.add(ScoreDetail.partial("EXPERIENCE_ENTRIES", "1 experience entry detected — add internships, projects, or roles if applicable", 10, 20));
        } else {
            details.add(ScoreDetail.fail("EXPERIENCE_ENTRIES", "No work experience section or entries detected", 20));
        }

        // Rule 6: Scannable Skills List Formatting (15 pts)
        String skills = doc.sections().getOrDefault(ResumeSection.SKILLS, "");
        if (skills.isBlank()) {
            details.add(ScoreDetail.fail("SKILLS_FORMAT", "Missing Skills section — group tools/languages in a scannable comma-separated list", 15));
        } else {
            boolean isList = skills.contains(",") || skills.contains("\u2022") || skills.contains("-")
                || skills.contains("*") || skills.contains("|") || skills.contains("\n");
            if (isList) {
                details.add(ScoreDetail.pass("SKILLS_FORMAT", "Skills section uses structured delimited formatting for instant scanning", 15));
            } else {
                details.add(ScoreDetail.partial("SKILLS_FORMAT", "Skills appear as unstructured prose — separate skills with commas or categories", 7, 15));
            }
        }

        // Rule 7: Zero Duplicate Lines (10 pts)
        if (!TextAnalyzer.hasDuplicateLines(doc.rawText())) {
            details.add(ScoreDetail.pass("NO_DUPLICATES", "Zero duplicate bullet or summary lines", 10));
        } else {
            details.add(ScoreDetail.partial("NO_DUPLICATES", "Duplicate content lines detected — remove redundant text", 4, 10));
        }

        return ScoreBreakdown.of("Formatting & Structure", details);
    }

    private int countIdealLengthBullets(List<String> bullets) {
        int count = 0;
        for (String b : bullets) {
            int words = TextAnalyzer.countWords(b);
            if (words >= 8 && words <= 30) count++;
        }
        return count;
    }

    private int calculateSectionOrderScore(Map<ResumeSection, String> sections) {
        List<ResumeSection> present = new ArrayList<>();
        for (ResumeSection s : PREFERRED_ORDER) {
            if (sections.containsKey(s)) present.add(s);
        }
        if (present.size() <= 1) return 50;

        int correctPairs = 0;
        int totalPairs = 0;
        List<ResumeSection> actualOrder = new ArrayList<>(sections.keySet());
        for (int i = 0; i < present.size(); i++) {
            for (int j = i + 1; j < present.size(); j++) {
                totalPairs++;
                int posI = actualOrder.indexOf(present.get(i));
                int posJ = actualOrder.indexOf(present.get(j));
                if (posI < posJ) correctPairs++;
            }
        }
        return totalPairs > 0 ? (int) (100.0 * correctPairs / totalPairs) : 50;
    }

    private int countExperienceEntries(ParsedDocument doc) {
        String experience = doc.sections().getOrDefault(ResumeSection.EXPERIENCE, "");
        if (experience.isBlank()) return 0;
        int dates = TextAnalyzer.countDates(experience);
        return Math.max(1, (dates + 1) / 2);
    }
}
