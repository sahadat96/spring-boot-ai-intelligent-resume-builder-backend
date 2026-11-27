package com.opticareer.app.modules.analysis.parsing;

import com.opticareer.app.modules.analysis.scoring.DomainKeywordDictionary;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TextAnalyzerTest {

    @Test
    void hasEmail_detectsStandardEmails() {
        assertThat(TextAnalyzer.hasEmail("contact me at sahadat@example.com")).isTrue();
        assertThat(TextAnalyzer.hasEmail("no email here")).isFalse();
    }

    @Test
    void hasPhone_detectsUsAndInternationalPhones() {
        assertThat(TextAnalyzer.hasPhone("Call (555) 123-4567")).isTrue();
        assertThat(TextAnalyzer.hasPhone("Phone: 555.123.4567")).isTrue();
        assertThat(TextAnalyzer.hasPhone("+880 1711-234567")).isTrue();
        assertThat(TextAnalyzer.hasPhone("+44 20 7946 0958")).isTrue();
        assertThat(TextAnalyzer.hasPhone("Worked 2021-2024 on platform")).isFalse();
        assertThat(TextAnalyzer.hasPhone("No phone")).isFalse();
    }

    @Test
    void countMetricBullets_detectsQuantifiedImpact() {
        List<String> bullets = List.of(
            "Reduced p95 latency by 40% to 12ms across checkout services",
            "Managed a team of developers",
            "Saved $50,000 annually in AWS infrastructure costs",
            "Scaled backend to serve 100K+ active users"
        );
        assertThat(TextAnalyzer.countMetricBullets(bullets)).isEqualTo(3);
    }

    @Test
    void countWeakStarterBulletsAndCliches_detectsPassiveLanguage() {
        List<String> bullets = List.of(
            "Responsible for building backend APIs",
            "Worked on frontend dashboards",
            "Architected event-driven Kafka consumer pipelines"
        );
        assertThat(TextAnalyzer.countWeakStarterBullets(bullets)).isEqualTo(2);
        assertThat(TextAnalyzer.countCliches("Hardworking team player and self-motivated go-getter")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void extractKeywords_canonicalizesAndFiltersNoise() {
        String text = "Senior developer experienced with ReactJS, NextJS, TS, SpringBoot, k8s, postgres, RESTful APIs in Jan 2024.";
        Set<String> domainKws = DomainKeywordDictionary.DOMAIN_KEYWORDS.get("SOFTWARE_ENGINEERING");
        List<String> keywords = TextAnalyzer.extractKeywords(text, domainKws, 10);

        assertThat(keywords).contains("React", "Next.js", "TypeScript", "Spring Boot", "Kubernetes", "PostgreSQL", "REST API");
        assertThat(keywords).doesNotContain("Jan", "2024");
    }

    @Test
    void matchJobDescription_identifiesMatchedAndMissingKeywords() {
        String resume = "Built microservices in Java, Spring Boot, PostgreSQL, Docker, and Git.";
        String jd = "Looking for Senior Backend Engineer with Java, Spring Boot, Kubernetes, Kafka, Redis, AWS, Docker.";

        TextAnalyzer.JobMatchResult match = TextAnalyzer.matchJobDescription(resume, "Senior Backend Engineer", jd);

        assertThat(match.matchScore()).isBetween(30, 80);
        assertThat(match.matchedKeywords()).contains("Java", "Spring Boot", "Docker");
        assertThat(match.missingKeywords()).contains("Kubernetes", "Kafka", "Redis", "AWS");
    }
}
