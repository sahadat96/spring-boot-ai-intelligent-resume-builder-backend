package com.opticareer.app.modules.analysis.scoring;

import java.util.Set;

public final class StopWordDictionary {
    private StopWordDictionary() {}

    public static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "can", "shall", "must",
        "need", "that", "which", "who", "whom", "this", "these", "those",
        "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you",
        "your", "yours", "yourself", "yourselves", "he", "him", "his",
        "himself", "she", "her", "hers", "herself", "it", "its", "itself",
        "they", "them", "their", "theirs", "themselves", "what", "when",
        "where", "why", "how", "all", "each", "every", "both", "few",
        "more", "most", "other", "some", "such", "no", "nor", "not",
        "only", "own", "same", "so", "than", "too", "very", "just",
        "about", "above", "after", "again", "also", "am", "any", "because",
        "before", "below", "between", "during", "here", "if", "into",
        "over", "then", "there", "through", "under", "until", "up", "while",
        "using", "used", "via", "across", "within", "including", "multiple"
    );

    public static final Set<String> RESUME_NOISE_WORDS = Set.of(
        "resume", "cv", "curriculum", "vitae", "summary", "objective", "profile",
        "experience", "work", "education", "skills", "certifications", "projects",
        "references", "available", "request", "present", "current", "jan", "feb",
        "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept", "oct", "nov",
        "dec", "january", "february", "march", "april", "june", "july", "august",
        "september", "october", "november", "december", "inc", "llc", "ltd",
        "corp", "corporation", "company", "team", "members", "role", "position",
        "responsibilities", "duties", "university", "college", "school", "degree",
        "gpa", "email", "phone", "address", "contact", "page", "etc", "year",
        "years", "month", "months", "daily", "monthly", "annual", "high", "new",
        "key", "strong", "various", "related", "plus", "based", "well", "good"
    );

    public static boolean isStopWord(String word) {
        String lower = word.toLowerCase();
        return STOP_WORDS.contains(lower) || RESUME_NOISE_WORDS.contains(lower);
    }
}
