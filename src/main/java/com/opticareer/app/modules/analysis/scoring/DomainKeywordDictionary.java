package com.opticareer.app.modules.analysis.scoring;

import java.util.*;

public final class DomainKeywordDictionary {
    private DomainKeywordDictionary() {}

    public static final Map<String, Set<String>> DOMAIN_KEYWORDS = Map.ofEntries(
        Map.entry("SOFTWARE_ENGINEERING", Set.of(
            "java", "python", "javascript", "typescript", "go", "rust", "c++", "c#",
            "react", "angular", "vue", "next.js", "node.js", "spring", "spring boot",
            "django", "flask", "fastapi", "express", "nest.js",
            "rest api", "graphql", "grpc", "microservices", "event-driven",
            "docker", "kubernetes", "aws", "azure", "gcp", "terraform", "ci/cd",
            "github actions", "jenkins", "git", "linux", "nginx",
            "sql", "nosql", "postgresql", "mysql", "mongodb", "redis", "kafka", "rabbitmq",
            "junit", "mockito", "jest", "playwright", "cypress", "selenium", "tdd",
            "html", "css", "tailwind", "webpack", "vite",
            "data structures", "algorithms", "oop", "design patterns", "system design",
            "api design", "devops", "observability", "prometheus", "grafana", "oauth2", "jwt"
        )),
        Map.entry("DATA_SCIENCE", Set.of(
            "python", "r", "sql", "machine learning", "deep learning", "llm", "rag",
            "tensorflow", "pytorch", "scikit-learn", "pandas", "numpy", "xgboost",
            "data visualization", "tableau", "power bi", "statistics", "probability",
            "natural language processing", "nlp", "computer vision", "transformers",
            "big data", "hadoop", "spark", "airflow", "dbt", "snowflake", "etl",
            "data pipeline", "a/b testing", "hypothesis testing", "regression",
            "classification", "clustering", "neural networks", "feature engineering",
            "model deployment", "mlops", "vector database", "pgvector"
        )),
        Map.entry("CLOUD_DEVOPS", Set.of(
            "aws", "azure", "gcp", "docker", "kubernetes", "helm", "terraform",
            "ansible", "ci/cd", "github actions", "gitlab ci", "jenkins", "argocd",
            "linux", "bash", "networking", "dns", "tls", "load balancing", "nginx",
            "prometheus", "grafana", "datadog", "elk", "opentelemetry", "sre",
            "incident management", "high availability", "disaster recovery", "iam", "vpc"
        )),
        Map.entry("PRODUCT_DESIGN", Set.of(
            "product management", "product strategy", "roadmap", "user research",
            "ui/ux", "figma", "wireframing", "prototyping", "design system",
            "usability testing", "accessibility", "wcag", "user stories", "backlog",
            "agile", "scrum", "a/b testing", "product analytics", "mixpanel",
            "amplitude", "funnel optimization", "retention", "go-to-market"
        )),
        Map.entry("BUSINESS_MANAGEMENT", Set.of(
            "project management", "stakeholder management", "strategic planning",
            "budgeting", "financial analysis", "roi", "kpi", "okr",
            "team leadership", "cross-functional", "agile", "scrum", "kanban",
            "risk management", "change management", "process improvement",
            "lean", "six sigma", "pmp", "mba", "operations",
            "supply chain", "vendor management", "negotiation",
            "business development", "market analysis", "competitive analysis"
        )),
        Map.entry("MARKETING", Set.of(
            "digital marketing", "seo", "sem", "ppc", "google ads", "meta ads",
            "social media", "content marketing", "email marketing",
            "brand management", "market research", "analytics",
            "google analytics", "hubspot", "salesforce", "crm",
            "conversion rate", "lead generation", "campaign management",
            "copywriting", "a/b testing", "roi", "kpi",
            "customer acquisition", "retention", "engagement", "growth marketing"
        )),
        Map.entry("HEALTHCARE", Set.of(
            "patient care", "clinical", "hipaa", "ehr", "emr", "epic",
            "medical records", "healthcare management", "nursing",
            "pharmacy", "diagnosis", "treatment", "rehabilitation",
            "mental health", "public health", "epidemiology",
            "fda", "clinical trials", "research", "cpr",
            "patient safety", "quality improvement", "compliance"
        )),
        Map.entry("FINANCE", Set.of(
            "financial analysis", "accounting", "gaap", "ifrs",
            "budgeting", "forecasting", "audit", "tax", "compliance",
            "risk management", "portfolio management", "investment",
            "bloomberg", "excel", "financial modeling", "valuation",
            "cfa", "cpa", "series 7", "derivatives", "equity",
            "fixed income", "banking", "fintech", "aml", "kyc"
        ))
    );

    public static final Map<String, String> ALIAS_TO_CANONICAL = Map.ofEntries(
        Map.entry("reactjs", "react"),
        Map.entry("react.js", "react"),
        Map.entry("nextjs", "next.js"),
        Map.entry("nodejs", "node.js"),
        Map.entry("nestjs", "nest.js"),
        Map.entry("vuejs", "vue"),
        Map.entry("vue.js", "vue"),
        Map.entry("ts", "typescript"),
        Map.entry("js", "javascript"),
        Map.entry("springboot", "spring boot"),
        Map.entry("spring-boot", "spring boot"),
        Map.entry("postgres", "postgresql"),
        Map.entry("k8s", "kubernetes"),
        Map.entry("rest", "rest api"),
        Map.entry("restful", "rest api"),
        Map.entry("restful api", "rest api"),
        Map.entry("restful apis", "rest api"),
        Map.entry("rest apis", "rest api"),
        Map.entry("cicd", "ci/cd"),
        Map.entry("ci cd", "ci/cd"),
        Map.entry("tailwindcss", "tailwind"),
        Map.entry("amazon web services", "aws"),
        Map.entry("google cloud", "gcp"),
        Map.entry("google cloud platform", "gcp"),
        Map.entry("microsoft azure", "azure"),
        Map.entry("golang", "go"),
        Map.entry("ml", "machine learning"),
        Map.entry("dl", "deep learning")
    );

    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry("java", "Java"),
        Map.entry("python", "Python"),
        Map.entry("javascript", "JavaScript"),
        Map.entry("typescript", "TypeScript"),
        Map.entry("go", "Go"),
        Map.entry("rust", "Rust"),
        Map.entry("c++", "C++"),
        Map.entry("c#", "C#"),
        Map.entry("react", "React"),
        Map.entry("angular", "Angular"),
        Map.entry("vue", "Vue"),
        Map.entry("next.js", "Next.js"),
        Map.entry("node.js", "Node.js"),
        Map.entry("spring", "Spring"),
        Map.entry("spring boot", "Spring Boot"),
        Map.entry("django", "Django"),
        Map.entry("flask", "Flask"),
        Map.entry("fastapi", "FastAPI"),
        Map.entry("express", "Express"),
        Map.entry("nest.js", "NestJS"),
        Map.entry("rest api", "REST API"),
        Map.entry("graphql", "GraphQL"),
        Map.entry("grpc", "gRPC"),
        Map.entry("microservices", "Microservices"),
        Map.entry("event-driven", "Event-Driven Architecture"),
        Map.entry("docker", "Docker"),
        Map.entry("kubernetes", "Kubernetes"),
        Map.entry("aws", "AWS"),
        Map.entry("azure", "Azure"),
        Map.entry("gcp", "GCP"),
        Map.entry("terraform", "Terraform"),
        Map.entry("ci/cd", "CI/CD"),
        Map.entry("github actions", "GitHub Actions"),
        Map.entry("jenkins", "Jenkins"),
        Map.entry("git", "Git"),
        Map.entry("linux", "Linux"),
        Map.entry("nginx", "Nginx"),
        Map.entry("sql", "SQL"),
        Map.entry("nosql", "NoSQL"),
        Map.entry("postgresql", "PostgreSQL"),
        Map.entry("mysql", "MySQL"),
        Map.entry("mongodb", "MongoDB"),
        Map.entry("redis", "Redis"),
        Map.entry("kafka", "Kafka"),
        Map.entry("rabbitmq", "RabbitMQ"),
        Map.entry("junit", "JUnit"),
        Map.entry("mockito", "Mockito"),
        Map.entry("jest", "Jest"),
        Map.entry("playwright", "Playwright"),
        Map.entry("cypress", "Cypress"),
        Map.entry("selenium", "Selenium"),
        Map.entry("tdd", "TDD"),
        Map.entry("html", "HTML"),
        Map.entry("css", "CSS"),
        Map.entry("tailwind", "Tailwind CSS"),
        Map.entry("webpack", "Webpack"),
        Map.entry("vite", "Vite"),
        Map.entry("data structures", "Data Structures"),
        Map.entry("algorithms", "Algorithms"),
        Map.entry("oop", "OOP"),
        Map.entry("design patterns", "Design Patterns"),
        Map.entry("system design", "System Design"),
        Map.entry("api design", "API Design"),
        Map.entry("devops", "DevOps"),
        Map.entry("observability", "Observability"),
        Map.entry("prometheus", "Prometheus"),
        Map.entry("grafana", "Grafana"),
        Map.entry("oauth2", "OAuth2"),
        Map.entry("jwt", "JWT"),
        Map.entry("agile", "Agile"),
        Map.entry("scrum", "Scrum"),
        Map.entry("machine learning", "Machine Learning"),
        Map.entry("deep learning", "Deep Learning"),
        Map.entry("tensorflow", "TensorFlow"),
        Map.entry("pytorch", "PyTorch"),
        Map.entry("scikit-learn", "scikit-learn"),
        Map.entry("pandas", "Pandas"),
        Map.entry("numpy", "NumPy"),
        Map.entry("tableau", "Tableau"),
        Map.entry("power bi", "Power BI"),
        Map.entry("nlp", "NLP"),
        Map.entry("llm", "LLM"),
        Map.entry("rag", "RAG"),
        Map.entry("mlops", "MLOps"),
        Map.entry("etl", "ETL"),
        Map.entry("a/b testing", "A/B Testing"),
        Map.entry("seo", "SEO"),
        Map.entry("sem", "SEM"),
        Map.entry("ppc", "PPC"),
        Map.entry("crm", "CRM"),
        Map.entry("roi", "ROI"),
        Map.entry("kpi", "KPI"),
        Map.entry("okr", "OKR"),
        Map.entry("hipaa", "HIPAA"),
        Map.entry("ehr", "EHR"),
        Map.entry("emr", "EMR"),
        Map.entry("fda", "FDA"),
        Map.entry("cpr", "CPR"),
        Map.entry("gaap", "GAAP"),
        Map.entry("ifrs", "IFRS"),
        Map.entry("cfa", "CFA"),
        Map.entry("cpa", "CPA"),
        Map.entry("ui/ux", "UI/UX"),
        Map.entry("figma", "Figma")
    );

    public static String canonicalize(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        return ALIAS_TO_CANONICAL.getOrDefault(lower, lower);
    }

    public static String displayName(String canonical) {
        if (canonical == null || canonical.isBlank()) return "";
        String lower = canonical.toLowerCase(Locale.ROOT).trim();
        String mapped = DISPLAY_NAMES.get(lower);
        if (mapped != null) return mapped;
        // Fallback Title Case
        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    public static Set<String> allKeywords() {
        Set<String> all = new HashSet<>();
        DOMAIN_KEYWORDS.values().forEach(all::addAll);
        return Collections.unmodifiableSet(all);
    }

    public static String detectDomain(String text) {
        if (text == null || text.isBlank()) return "SOFTWARE_ENGINEERING";
        String lower = text.toLowerCase(Locale.ROOT);
        String bestDomain = "SOFTWARE_ENGINEERING";
        int bestCount = 0;
        for (var entry : DOMAIN_KEYWORDS.entrySet()) {
            int count = 0;
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                bestDomain = entry.getKey();
            }
        }
        return bestDomain;
    }
}
