package com.opticareer.app.modules.analysis.domain;

public enum ResumeSection {
    CONTACT("Contact Information"),
    SUMMARY("Professional Summary"),
    OBJECTIVE("Career Objective"),
    EXPERIENCE("Work Experience"),
    EDUCATION("Education"),
    SKILLS("Skills"),
    CERTIFICATIONS("Certifications"),
    PROJECTS("Projects"),
    AWARDS("Awards & Achievements"),
    PUBLICATIONS("Publications"),
    LANGUAGES("Languages"),
    VOLUNTEER("Volunteer Experience"),
    REFERENCES("References"),
    UNKNOWN("Unknown Section");

    private final String displayName;

    ResumeSection(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
