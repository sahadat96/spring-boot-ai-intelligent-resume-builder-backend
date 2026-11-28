package com.opticareer.app.modules.dashboard.domain;

import java.util.List;

/**
 * Curated industry role ontology mapping canonical core and supporting skills
 * across engineering, cloud/DevOps, data/AI, product, business, marketing, finance, and healthcare.
 */
public final class CareerRoleCatalog {

    private CareerRoleCatalog() {}

    public record RoleDefinition(
        String title,
        String domain,
        String domainLabel,
        List<String> coreSkills,
        List<String> supportingSkills
    ) {}

    public static final List<RoleDefinition> ROLES = List.of(
        // --- Cloud & DevOps ---
        new RoleDefinition(
            "DevOps Engineer", "CLOUD_DEVOPS", "cloud & DevOps engineering",
            List.of("docker", "kubernetes", "terraform", "ci/cd", "aws", "linux", "bash"),
            List.of("git", "github actions", "jenkins", "nginx", "prometheus", "grafana", "python", "ansible")
        ),
        new RoleDefinition(
            "Cloud Engineer", "CLOUD_DEVOPS", "cloud infrastructure",
            List.of("aws", "azure", "gcp", "terraform", "linux", "networking", "iam", "docker"),
            List.of("vpc", "dns", "kubernetes", "ci/cd", "python", "bash", "load balancing")
        ),
        new RoleDefinition(
            "Site Reliability Engineer", "CLOUD_DEVOPS", "site reliability & observability",
            List.of("kubernetes", "linux", "prometheus", "grafana", "terraform", "sre", "incident management"),
            List.of("docker", "aws", "high availability", "disaster recovery", "opentelemetry", "go", "python")
        ),
        new RoleDefinition(
            "Platform Engineer", "CLOUD_DEVOPS", "internal developer platforms",
            List.of("kubernetes", "terraform", "ci/cd", "docker", "helm", "argocd", "aws"),
            List.of("github actions", "gitlab ci", "go", "python", "observability", "linux")
        ),

        // --- Software Engineering ---
        new RoleDefinition(
            "Backend Engineer", "SOFTWARE_ENGINEERING", "backend engineering",
            List.of("java", "spring boot", "postgresql", "rest api", "docker", "redis", "sql"),
            List.of("microservices", "kafka", "junit", "git", "oauth2", "jwt", "aws", "system design")
        ),
        new RoleDefinition(
            "Java / Spring Boot Engineer", "SOFTWARE_ENGINEERING", "Java & Spring ecosystem",
            List.of("java", "spring boot", "spring", "postgresql", "rest api", "junit", "docker"),
            List.of("redis", "kafka", "microservices", "sql", "mockito", "git", "ci/cd", "oauth2")
        ),
        new RoleDefinition(
            "Full-Stack Engineer", "SOFTWARE_ENGINEERING", "full-stack web engineering",
            List.of("react", "typescript", "node.js", "next.js", "rest api", "postgresql", "docker"),
            List.of("javascript", "tailwind", "mongodb", "redis", "git", "ci/cd", "graphql", "html", "css")
        ),
        new RoleDefinition(
            "Frontend Engineer", "SOFTWARE_ENGINEERING", "frontend web architecture",
            List.of("react", "typescript", "javascript", "next.js", "html", "css", "tailwind"),
            List.of("vite", "webpack", "jest", "playwright", "cypress", "git", "rest api", "graphql")
        ),
        new RoleDefinition(
            "Python Backend Engineer", "SOFTWARE_ENGINEERING", "Python backend services",
            List.of("python", "fastapi", "django", "flask", "postgresql", "rest api", "docker"),
            List.of("redis", "sql", "pytest", "aws", "git", "linux", "ci/cd", "microservices")
        ),
        new RoleDefinition(
            "Software Engineer", "SOFTWARE_ENGINEERING", "core software engineering",
            List.of("java", "python", "javascript", "sql", "git", "rest api", "data structures"),
            List.of("algorithms", "oop", "design patterns", "docker", "agile", "scrum", "linux", "tdd")
        ),

        // --- Data & AI ---
        new RoleDefinition(
            "Data Scientist", "DATA_SCIENCE", "data science & modeling",
            List.of("python", "machine learning", "pandas", "numpy", "scikit-learn", "sql", "statistics"),
            List.of("a/b testing", "hypothesis testing", "regression", "classification", "data visualization", "tableau")
        ),
        new RoleDefinition(
            "Machine Learning Engineer", "DATA_SCIENCE", "applied machine learning",
            List.of("python", "pytorch", "tensorflow", "machine learning", "deep learning", "mlops", "docker"),
            List.of("scikit-learn", "feature engineering", "model deployment", "nlp", "computer vision", "aws", "sql")
        ),
        new RoleDefinition(
            "AI / LLM Engineer", "DATA_SCIENCE", "generative AI & LLM systems",
            List.of("python", "llm", "rag", "nlp", "vector database", "transformers", "fastapi"),
            List.of("pytorch", "docker", "aws", "rest api", "pgvector", "deep learning", "mlops")
        ),
        new RoleDefinition(
            "Data Engineer", "DATA_SCIENCE", "data pipelines & warehousing",
            List.of("python", "sql", "etl", "data pipeline", "spark", "airflow", "snowflake"),
            List.of("dbt", "kafka", "postgresql", "big data", "hadoop", "aws", "docker")
        ),
        new RoleDefinition(
            "Data Analyst", "DATA_SCIENCE", "analytics & business intelligence",
            List.of("sql", "tableau", "power bi", "excel", "data visualization", "statistics"),
            List.of("python", "r", "a/b testing", "kpi", "etl", "reporting")
        ),

        // --- Product, Design & Management ---
        new RoleDefinition(
            "Product Manager", "PRODUCT_DESIGN", "product strategy & delivery",
            List.of("product management", "product strategy", "roadmap", "user research", "agile", "scrum"),
            List.of("a/b testing", "product analytics", "user stories", "backlog", "kpi", "okr", "go-to-market")
        ),
        new RoleDefinition(
            "UI/UX Designer", "PRODUCT_DESIGN", "product & interface design",
            List.of("ui/ux", "figma", "wireframing", "prototyping", "design system", "usability testing"),
            List.of("accessibility", "wcag", "user research", "html", "css")
        ),
        new RoleDefinition(
            "Technical Project Manager", "BUSINESS_MANAGEMENT", "technical program delivery",
            List.of("project management", "agile", "scrum", "stakeholder management", "risk management", "kpi"),
            List.of("kanban", "okr", "budgeting", "cross-functional", "change management", "pmp")
        ),
        new RoleDefinition(
            "Business Analyst", "BUSINESS_MANAGEMENT", "business & process analysis",
            List.of("financial analysis", "stakeholder management", "process improvement", "kpi", "sql", "excel"),
            List.of("agile", "market analysis", "strategic planning", "tableau", "roi")
        ),

        // --- Marketing, Finance & Healthcare ---
        new RoleDefinition(
            "Digital Marketing Specialist", "MARKETING", "digital & performance marketing",
            List.of("digital marketing", "seo", "sem", "ppc", "google ads", "google analytics", "campaign management"),
            List.of("content marketing", "email marketing", "social media", "meta ads", "copywriting", "conversion rate")
        ),
        new RoleDefinition(
            "Growth Marketing Manager", "MARKETING", "growth & lifecycle marketing",
            List.of("growth marketing", "a/b testing", "conversion rate", "customer acquisition", "retention", "analytics"),
            List.of("seo", "hubspot", "crm", "roi", "kpi", "lead generation", "email marketing")
        ),
        new RoleDefinition(
            "Financial Analyst", "FINANCE", "financial modeling & valuation",
            List.of("financial analysis", "financial modeling", "excel", "valuation", "budgeting", "forecasting"),
            List.of("accounting", "gaap", "ifrs", "risk management", "bloomberg", "cfa")
        ),
        new RoleDefinition(
            "FinTech Compliance Analyst", "FINANCE", "fintech risk & regulatory compliance",
            List.of("fintech", "compliance", "risk management", "aml", "kyc", "audit"),
            List.of("financial analysis", "banking", "sql", "accounting")
        ),
        new RoleDefinition(
            "Clinical / Healthcare Analyst", "HEALTHCARE", "clinical operations & healthcare data",
            List.of("healthcare management", "hipaa", "ehr", "emr", "clinical", "patient safety"),
            List.of("quality improvement", "compliance", "public health", "medical records", "research", "sql")
        )
    );
}
