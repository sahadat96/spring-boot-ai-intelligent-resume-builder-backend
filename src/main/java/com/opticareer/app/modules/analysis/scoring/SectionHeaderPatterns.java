package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.ResumeSection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class SectionHeaderPatterns {
    private SectionHeaderPatterns() {}

    private static final Map<Pattern, ResumeSection> PATTERNS = new LinkedHashMap<>();

    static {
        add("(?:contact|personal)\\s*(?:info(?:rmation)?|details)?", ResumeSection.CONTACT);
        add("(?:professional\\s+)?summary|profile|about\\s*me", ResumeSection.SUMMARY);
        add("(?:career\\s+)?objective", ResumeSection.OBJECTIVE);
        add("(?:work|professional|employment|career)\\s*(?:experience|history)|experience", ResumeSection.EXPERIENCE);
        add("education(?:al)?(?:\\s+(?:background|history|qualifications))?|academic", ResumeSection.EDUCATION);
        add("(?:technical\\s+|core\\s+|key\\s+|relevant\\s+)?skills(?:\\s+(?:and|&)\\s+(?:competencies|expertise|abilities))?|competencies|expertise|proficiencies|technologies", ResumeSection.SKILLS);
        add("certifications?|licenses?|accreditations?|credentials", ResumeSection.CERTIFICATIONS);
        add("(?:personal|academic|selected)?\\s*projects?", ResumeSection.PROJECTS);
        add("(?:awards?|honors?|achievements?|recognition)(?:\\s+(?:and|&)\\s+(?:honors?|achievements?))?", ResumeSection.AWARDS);
        add("publications?|papers?|research(?:\\s+publications?)?", ResumeSection.PUBLICATIONS);
        add("languages?|linguistic", ResumeSection.LANGUAGES);
        add("volunteer(?:ing)?(?:\\s+(?:experience|work))?|community\\s+(?:service|involvement)", ResumeSection.VOLUNTEER);
        add("references?", ResumeSection.REFERENCES);
    }

    private static void add(String regex, ResumeSection section) {
        PATTERNS.put(Pattern.compile("^\\s*" + regex + "\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), section);
    }

    public static Map<Pattern, ResumeSection> patterns() {
        return PATTERNS;
    }
}
