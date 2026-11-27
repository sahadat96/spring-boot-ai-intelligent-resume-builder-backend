package com.opticareer.app.modules.analysis.parsing;

import com.opticareer.app.modules.analysis.scoring.DomainKeywordDictionary;
import com.opticareer.app.modules.analysis.scoring.StopWordDictionary;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextAnalyzer {

    private TextAnalyzer() {}

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
    );
    // Supports North American AND international E.164/spaced phone numbers (7-15 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+?\\d{1,3}[\\s.-]?)?(?:\\(?\\d{2,4}\\)?[\\s.-]?)?\\d{3,4}[\\s.-]?\\d{3,4}(?:[\\s.-]?\\d{2,4})?"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?:https?://|www\\.)[\\w\\-]+(?:\\.[\\w\\-]+)+[/\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]*|(?:linkedin\\.com/in|github\\.com)/[\\w\\-]+"
    );
    private static final Pattern METRIC_PATTERN = Pattern.compile(
        "\\d+(?:\\.\\d+)?\\s*(?:%|percent|\\$|€|£|₹|৳|dollars?|users?|customers?|clients?|employees?|engineers?|members?|teams?|projects?|applications?|apis?|services?|servers?|transactions?|requests?|records?|ms\\b|sec(?:onds?)?|months?|years?|hours?|minutes?|days?|weeks?|x\\b|K\\b|M\\b|B\\b|\\+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOLLAR_AMOUNT = Pattern.compile(
        "[$€£₹৳]\\s*\\d[\\d,]*(?:\\.\\d{1,2})?(?:[KMBkmb])?"
    );
    private static final Pattern PERCENTAGE = Pattern.compile(
        "\\d+(?:\\.\\d+)?\\s*%"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4}\\s*[-\u2013\u2014to]+\\s*(?:present|current|now|\\d{4}))",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRONOUN_PATTERN = Pattern.compile(
        "\\b(?:I|me|my|myself|mine)\\b"
    );
    private static final Pattern WEAK_STARTER_PATTERN = Pattern.compile(
        "^(?:responsible\\s+for|duties\\s+included?|helped\\s+(?:with|to)?|worked\\s+(?:on|with)|assisted\\s+(?:with|in)?|tasked\\s+with|participated\\s+in|involved\\s+in)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final List<String> CLICHE_PHRASES = List.of(
        "hard worker", "hardworking", "team player", "self-motivated",
        "detail-oriented", "detail oriented", "go-getter", "think outside the box",
        "synergy", "fast learner", "quick learner", "results-driven professional",
        "people person", "wear many hats"
    );

    public record JobMatchResult(int matchScore, List<String> matchedKeywords, List<String> missingKeywords) {}

    public static boolean hasEmail(String text) {
        return text != null && EMAIL_PATTERN.matcher(text).find();
    }

    public static boolean hasPhone(String text) {
        if (text == null) return false;
        Matcher m = PHONE_PATTERN.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("\\D", "");
            // Phone numbers have 7 to 15 digits and must not be pure 4-digit years or date ranges (e.g. 2021-2024 = 8 digits)
            if (digits.length() >= 7 && digits.length() <= 15 && !m.group().matches("^\\d{4}[-–]\\d{4}$")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUrl(String text, List<String> annotationUrls) {
        if (annotationUrls != null && !annotationUrls.isEmpty()) return true;
        return text != null && URL_PATTERN.matcher(text).find();
    }

    public static boolean hasUrl(String text) {
        return hasUrl(text, List.of());
    }

    public static int countMetrics(String text) {
        if (text == null) return 0;
        int count = 0;
        Matcher m1 = METRIC_PATTERN.matcher(text);
        while (m1.find()) count++;
        Matcher m2 = DOLLAR_AMOUNT.matcher(text);
        while (m2.find()) count++;
        Matcher m3 = PERCENTAGE.matcher(text);
        while (m3.find()) count++;
        return count;
    }

    public static int countMetricBullets(List<String> bullets) {
        int count = 0;
        for (String bullet : bullets) {
            if (METRIC_PATTERN.matcher(bullet).find()
                || DOLLAR_AMOUNT.matcher(bullet).find()
                || PERCENTAGE.matcher(bullet).find()) {
                count++;
            }
        }
        return count;
    }

    public static int countWeakStarterBullets(List<String> bullets) {
        int count = 0;
        for (String bullet : bullets) {
            if (WEAK_STARTER_PATTERN.matcher(bullet.trim()).find()) {
                count++;
            }
        }
        return count;
    }

    public static int countCliches(String text) {
        if (text == null || text.isBlank()) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String phrase : CLICHE_PHRASES) {
            if (lower.contains(phrase)) count++;
        }
        return count;
    }

    public static int countDates(String text) {
        if (text == null) return 0;
        int count = 0;
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) count++;
        return count;
    }

    public static int countPronouns(String text) {
        if (text == null) return 0;
        int count = 0;
        Matcher m = PRONOUN_PATTERN.matcher(text);
        while (m.find()) count++;
        return count;
    }

    public static boolean hasUnicodeBullets(String text) {
        return text != null && text.chars().anyMatch(c -> c == '\u2022' || c == '\u25BA' || c == '\u25CF'
            || c == '\u25CB' || c == '\u25A0' || c == '\u25AA' || c == '\u2192'
            || c == '\u2713' || c == '\u2714' || c == '\u27A4');
    }

    public static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    public static double averageSentenceLength(String text) {
        if (text == null || text.isBlank()) return 0;
        String[] sentences = text.split("[.!?\n]+");
        if (sentences.length == 0) return 0;
        int totalWords = 0;
        int validSentences = 0;
        for (String s : sentences) {
            int words = countWords(s.trim());
            if (words > 2) {
                totalWords += words;
                validSentences++;
            }
        }
        return validSentences > 0 ? (double) totalWords / validSentences : 0;
    }

    public static int longestLineLength(String text) {
        if (text == null) return 0;
        return text.lines()
            .mapToInt(String::length)
            .max()
            .orElse(0);
    }

    public static boolean hasDuplicateLines(String text) {
        if (text == null) return false;
        List<String> lines = text.lines()
            .map(String::trim)
            .filter(l -> l.length() > 25)
            .toList();
        return lines.size() != new HashSet<>(lines).size();
    }

    public static boolean hasConsistentBullets(List<String> bullets, String fullText) {
        if (bullets.size() < 3 || fullText == null) return true;
        Pattern markers = Pattern.compile("^\\s*([\u2022\\-*\u25AA\u25BA\u25CF\u25CB\u25A0\u2192>])");
        Map<String, Integer> markerCounts = new HashMap<>();
        for (String line : fullText.split("\n")) {
            Matcher m = markers.matcher(line);
            if (m.find()) {
                markerCounts.merge(m.group(1), 1, Integer::sum);
            }
        }
        if (markerCounts.isEmpty()) return true;
        int max = markerCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int total = markerCounts.values().stream().mapToInt(Integer::intValue).sum();
        return total == 0 || (double) max / total >= 0.75;
    }

    /**
     * Canonicalized section-aware TF-IDF keyword extraction returning clean display names
     * (e.g., "React", "Spring Boot", "REST API").
     */
    public static List<String> extractKeywords(String text, Set<String> domainKeywords, int topN) {
        return extractKeywords(text, null, domainKeywords, topN);
    }

    public static List<String> extractKeywords(
            String text,
            String skillsSectionText,
            Set<String> domainKeywords,
            int topN) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);
        String skillsLower = skillsSectionText != null ? skillsSectionText.toLowerCase(Locale.ROOT) : "";
        Set<String> knownAll = DomainKeywordDictionary.allKeywords();

        Map<String, Double> weightedScores = new LinkedHashMap<>();

        // 1. Multi-word & exact known skill scan weighted by domain relevance + explicit Skills-section IDF boost
        for (String kw : knownAll) {
            Pattern kwPattern = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(kw) + "(?![a-z0-9])");
            Matcher m = kwPattern.matcher(lower);
            int hits = 0;
            while (m.find()) hits++;
            if (hits > 0) {
                double idf = kw.contains(" ") ? 1.35 : 1.15;
                double domainWeight = domainKeywords.contains(kw) ? 4.0 : 2.5;
                double skillsBoost = (!skillsLower.isEmpty() && kwPattern.matcher(skillsLower).find()) ? 1.5 : 1.0;
                weightedScores.merge(kw, Math.min(hits, 4) * domainWeight * idf * skillsBoost, Double::sum);
            }
        }

        // 2. Alias detection & canonical mapping
        for (var aliasEntry : DomainKeywordDictionary.ALIAS_TO_CANONICAL.entrySet()) {
            Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(aliasEntry.getKey()) + "(?![a-z0-9])");
            Matcher m = p.matcher(lower);
            int hits = 0;
            while (m.find()) hits++;
            if (hits > 0) {
                String canonical = aliasEntry.getValue();
                double domainWeight = domainKeywords.contains(canonical) ? 4.0 : 2.5;
                double skillsBoost = (!skillsLower.isEmpty() && p.matcher(skillsLower).find()) ? 1.5 : 1.0;
                weightedScores.merge(canonical, Math.min(hits, 4) * domainWeight * 1.2 * skillsBoost, Double::sum);
            }
        }

        // 3. Fallback TF-IDF for significant domain tokens not yet covered by canonical dictionary
        String[] tokens = lower.split("[^a-z0-9.#+/\\-]+");
        Map<String, Integer> tf = new LinkedHashMap<>();
        for (String rawToken : tokens) {
            String token = rawToken.replaceAll("^[.#/+\\-]+|[.#/+\\-]+$", "");
            if (token.length() < 2 || token.matches("\\d+") || StopWordDictionary.isStopWord(token)) continue;
            String canonical = DomainKeywordDictionary.canonicalize(token);
            tf.merge(canonical, 1, Integer::sum);
        }

        int totalTokens = Math.max(1, tf.values().stream().mapToInt(Integer::intValue).sum());
        for (var entry : tf.entrySet()) {
            String kw = entry.getKey();
            if (weightedScores.containsKey(kw)) continue;
            if (entry.getValue() < 2 && !knownAll.contains(kw)) continue;
            double termFreq = (double) entry.getValue() / totalTokens;
            double idfProxy = Math.log(1.0 + ((double) totalTokens / (entry.getValue() + 1)));
            weightedScores.put(kw, termFreq * idfProxy);
        }

        return weightedScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topN)
            .map(e -> DomainKeywordDictionary.displayName(e.getKey()))
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Evaluates resume alignment against a target role or job description using normalized skill matching
     * + token overlap (weighted towards technical/domain keywords).
     */
    public static JobMatchResult matchJobDescription(String resumeText, String targetRole, String jobDescription) {
        String combinedJd = ((targetRole != null ? targetRole + " " : "")
            + (jobDescription != null ? jobDescription : "")).trim();
        if (combinedJd.isBlank() || resumeText == null || resumeText.isBlank()) {
            return new JobMatchResult(0, List.of(), List.of());
        }

        String jdDomain = DomainKeywordDictionary.detectDomain(combinedJd);
        Set<String> jdDomainKws = DomainKeywordDictionary.DOMAIN_KEYWORDS.getOrDefault(jdDomain, Set.of());
        List<String> jdKeywords = extractKeywords(combinedJd, jdDomainKws, 25);

        String resumeLower = resumeText.toLowerCase(Locale.ROOT);
        Set<String> resumeExtracted = extractKeywords(resumeText, DomainKeywordDictionary.allKeywords(), 40)
            .stream()
            .map(DomainKeywordDictionary::canonicalize)
            .collect(Collectors.toSet());

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String jdKwDisplay : jdKeywords) {
            String canonical = DomainKeywordDictionary.canonicalize(jdKwDisplay);
            boolean present = resumeExtracted.contains(canonical)
                || resumeLower.contains(canonical)
                || containsAnyAlias(resumeLower, canonical);
            if (present) {
                matched.add(jdKwDisplay);
            } else {
                missing.add(jdKwDisplay);
            }
        }

        int total = matched.size() + missing.size();
        int score = total > 0 ? Math.min(100, (int) Math.round((matched.size() * 100.0) / total)) : 0;
        return new JobMatchResult(score, matched, missing);
    }

    private static boolean containsAnyAlias(String lowerText, String canonical) {
        for (var entry : DomainKeywordDictionary.ALIAS_TO_CANONICAL.entrySet()) {
            if (entry.getValue().equals(canonical) && lowerText.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }
}
