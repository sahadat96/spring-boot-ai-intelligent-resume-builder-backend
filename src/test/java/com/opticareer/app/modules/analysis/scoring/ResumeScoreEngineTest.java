package com.opticareer.app.modules.analysis.scoring;

import com.opticareer.app.modules.analysis.domain.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumeScoreEngineTest {

    private ResumeScoreEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ResumeScoreEngine(
            new AtsScoreCalculator(),
            new ContentScoreCalculator(),
            new FormattingScoreCalculator()
        );
    }

    @Test
    void analyze_producesCompleteBreakdownKeywordsAndMissingJdKeywords() {
        String text = """
            Jane Smith
            jane@example.com | (555) 987-6543 | github.com/janesmith

            SUMMARY
            Experienced full-stack developer with 4+ years building resilient APIs and React frontends for high-growth SaaS products.

            EXPERIENCE
            Lead Developer | BigCo | Mar 2021 - Present
            - Led development of customer portal serving 100K monthly active users
            - Increased automated test coverage from 45% to 92% across 3 microservices
            - Managed team of 4 developers using Agile methodology to ship 12 features

            Developer | SmallCo | Jan 2019 - Feb 2021
            - Built responsive web applications using React and TypeScript
            - Automated deployment pipeline reducing release cycle from 7 days to 1 day

            EDUCATION
            Master of Science in Software Engineering | Stanford | May 2018

            SKILLS
            React, TypeScript, Node.js, Python, Docker, PostgreSQL, MongoDB, Git, CI/CD, REST API
            """;

        Map<ResumeSection, String> sections = new LinkedHashMap<>();
        sections.put(ResumeSection.CONTACT, "Jane Smith\njane@example.com | (555) 987-6543");
        sections.put(ResumeSection.SUMMARY, "Experienced full-stack developer with 4+ years building resilient APIs and React frontends.");
        sections.put(ResumeSection.EXPERIENCE, "Lead Developer | BigCo | Mar 2021 - Present\nDeveloper | SmallCo | Jan 2019 - Feb 2021");
        sections.put(ResumeSection.EDUCATION, "Master of Science in Software Engineering | Stanford | May 2018");
        sections.put(ResumeSection.SKILLS, "React, TypeScript, Node.js, Python, Docker, PostgreSQL, MongoDB, Git, CI/CD, REST API");

        List<String> bullets = List.of(
            "Led development of customer portal serving 100K monthly active users",
            "Increased automated test coverage from 45% to 92% across 3 microservices",
            "Managed team of 4 developers using Agile methodology to ship 12 features",
            "Built responsive web applications using React and TypeScript",
            "Automated deployment pipeline reducing release cycle from 7 days to 1 day"
        );

        ParsedDocument doc = new ParsedDocument(text, sections, bullets, 1, false, "2.0.0", 30);
        AnalysisResult result = engine.analyze(
            doc,
            "Senior Platform Engineer",
            "Required: React, TypeScript, Kubernetes, Terraform, AWS, Kafka"
        );

        assertThat(result.overallScore()).isBetween(60, 100);
        assertThat(result.jdMatchScore()).isNotNull().isBetween(0, 100);
        assertThat(result.keywords()).contains("React", "TypeScript");
        assertThat(result.missingKeywords()).contains("Kubernetes", "Terraform", "AWS", "Kafka");
        assertThat(result.strengths()).isNotEmpty();
        assertThat(result.areasToImprove()).isNotEmpty();
    }

    @Test
    void analyze_overallScoreMatchesWeightedFormulaWithoutJd() {
        ParsedDocument doc = new ParsedDocument("test@test.com 555-123-4567", Map.of(), List.of(), 1, false, "2.0.0", 10);
        AnalysisResult result = engine.analyze(doc);

        int expected = (int) Math.round(
            result.atsScore() * 0.35
            + result.contentScore() * 0.35
            + result.formattingScore() * 0.30
        );

        assertThat(result.jdMatchScore()).isNull();
        assertThat(result.overallScore()).isCloseTo(expected, within(1));
    }
}
