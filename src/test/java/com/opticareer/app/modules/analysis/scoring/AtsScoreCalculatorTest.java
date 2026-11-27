package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AtsScoreCalculatorTest {

    private AtsScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AtsScoreCalculator();
    }

    @Test
    void wellStructuredResume_scoresAbove80() {
        String text = """
            John Doe
            john.doe@email.com | (555) 123-4567 | linkedin.com/in/johndoe

            PROFESSIONAL SUMMARY
            Results-driven software engineer with 5+ years of experience building scalable web applications using Java, Spring Boot, React, and AWS cloud services.

            EXPERIENCE
            Senior Software Engineer | TechCorp Inc. | Jan 2022 - Present
            - Developed microservices architecture serving 50K daily users using Spring Boot and Docker
            - Reduced API response time by 40% through Redis caching and query optimization
            - Led a team of 5 engineers to deliver a payment processing system handling $2M monthly

            Software Engineer | StartupXYZ | Jun 2019 - Dec 2021
            - Built REST APIs handling 10K requests per second with 99.9% uptime
            - Implemented CI/CD pipeline reducing deployment time from 2 hours to 15 minutes
            - Designed database schema for PostgreSQL supporting 1M+ records

            EDUCATION
            Bachelor of Science in Computer Science | MIT | May 2019

            SKILLS
            Java, Spring Boot, React, TypeScript, Docker, Kubernetes, AWS, PostgreSQL, Redis, Git, Agile, REST API, Microservices
            """;

        Map<ResumeSection, String> sections = Map.of(
            ResumeSection.CONTACT, "John Doe\njohn.doe@email.com | (555) 123-4567",
            ResumeSection.SUMMARY, "Results-driven software engineer with 5+ years of experience building scalable web applications.",
            ResumeSection.EXPERIENCE, "Senior Software Engineer | TechCorp Inc. | Jan 2022 - Present\nSoftware Engineer | StartupXYZ | Jun 2019 - Dec 2021",
            ResumeSection.EDUCATION, "Bachelor of Science in Computer Science | MIT | May 2019",
            ResumeSection.SKILLS, "Java, Spring Boot, React, TypeScript, Docker, Kubernetes, AWS, PostgreSQL, Redis, Git, Agile, REST API, Microservices"
        );

        List<String> bullets = List.of(
            "Developed microservices architecture serving 50K daily users using Spring Boot and Docker",
            "Reduced API response time by 40% through Redis caching and query optimization",
            "Led a team of 5 engineers to deliver a payment processing system handling $2M monthly",
            "Built REST APIs handling 10K requests per second with 99.9% uptime",
            "Implemented CI/CD pipeline reducing deployment time from 2 hours to 15 minutes",
            "Designed database schema for PostgreSQL supporting 1M+ records"
        );

        PdfLayoutMetadata layout = new PdfLayoutMetadata(Set.of("Helvetica"), 10.5f, 16f, 0, 0, List.of(), 0.0);
        ParsedDocument doc = new ParsedDocument(text, sections, bullets, 1, false, "2.0.0", 50, layout);
        ScoreBreakdown result = calculator.calculate(doc);

        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        assertThat(result.category()).isEqualTo("ATS Compatibility");
        assertThat(result.details()).isNotEmpty();
    }

    @Test
    void minimalResume_scoresBelow50() {
        ParsedDocument doc = new ParsedDocument("Hello world this is my resume.", Map.of(), List.of(), 1, false, "2.0.0", 10);
        ScoreBreakdown result = calculator.calculate(doc);

        assertThat(result.score()).isLessThan(50);
    }

    @Test
    void ocrAndMultiColumn_penalizesAtsScore() {
        String text = "john@email.com\n555-123-4567\nExperience\nSkills: Java, Python\nEducation\nSummary";
        Map<ResumeSection, String> sections = Map.of(
            ResumeSection.EXPERIENCE, "Some experience",
            ResumeSection.SKILLS, "Java, Python",
            ResumeSection.EDUCATION, "BS CS"
        );

        PdfLayoutMetadata badLayout = new PdfLayoutMetadata(Set.of("CustomFont"), 10f, 10f, 6, 4, List.of(), 0.0);
        PdfLayoutMetadata goodLayout = new PdfLayoutMetadata(Set.of("Arial"), 10.5f, 15f, 0, 0, List.of(), 0.0);

        ParsedDocument withOcrAndColumns = new ParsedDocument(text, sections, List.of(), 1, true, "2.0.0", 50, badLayout);
        ParsedDocument clean = new ParsedDocument(text, sections, List.of(), 1, false, "2.0.0", 50, goodLayout);

        assertThat(calculator.calculate(clean).score())
            .isGreaterThan(calculator.calculate(withOcrAndColumns).score() + 15);
    }
}
