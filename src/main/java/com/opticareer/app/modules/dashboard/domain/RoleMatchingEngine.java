package com.opticareer.app.modules.dashboard.domain;

import com.opticareer.app.modules.analysis.AnalysisApi.CompletedAnalysisSnapshot;
import com.opticareer.app.modules.analysis.scoring.DomainKeywordDictionary;
import com.opticareer.app.modules.dashboard.domain.CareerRoleCatalog.RoleDefinition;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BestFitRole;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.BiggestOpportunity;
import com.opticareer.app.modules.dashboard.domain.DashboardSnapshot.RecommendedMove;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic career-intelligence engine turning a completed resume analysis
 * into ranked role matches, biggest gap opportunity with match-lift calculation,
 * evidence-backed profile highlights, and prioritized next moves.
 */
@Component
public class RoleMatchingEngine {

    public List<BestFitRole> rankRoles(
            CompletedAnalysisSnapshot analysis,
            String profileHeadline,
            int limit) {
        if (analysis == null) {
            return List.of();
        }

        String rawText = analysis.extractedText() != null ? analysis.extractedText() : "";
        String lowerText = rawText.toLowerCase(Locale.ROOT);
        String skillsSection = sectionText(analysis.sections(), "SKILLS");
        String experienceSection = sectionText(analysis.sections(), "EXPERIENCE");
        String summarySection = sectionText(analysis.sections(), "SUMMARY");

        Set<String> canonicalResumeKeywords = canonicalSet(analysis.keywords());
        String dominantDomain = DomainKeywordDictionary.detectDomain(lowerText);
        String combinedTitleContext = (
            (analysis.targetRole() != null ? analysis.targetRole() + " " : "")
                + (profileHeadline != null ? profileHeadline + " " : "")
                + summarySection + " "
                + firstLines(experienceSection, 12)
        ).toLowerCase(Locale.ROOT);

        List<BestFitRole> scored = new ArrayList<>();
        for (RoleDefinition role : CareerRoleCatalog.ROLES) {
            BestFitRole fit = evaluateRole(
                role, lowerText, skillsSection, experienceSection, combinedTitleContext,
                canonicalResumeKeywords, dominantDomain, analysis.overallScore()
            );
            scored.add(fit);
        }

        scored.sort(
            Comparator.comparingInt(BestFitRole::matchPercentage).reversed()
                .thenComparing(r -> matchesPreferredTitle(r.roleTitle(), analysis.targetRole()), Comparator.reverseOrder())
                .thenComparing(BestFitRole::roleTitle)
        );

        int safeLimit = Math.max(1, Math.min(limit, CareerRoleCatalog.ROLES.size()));
        return scored.stream().limit(safeLimit).toList();
    }

    public String resolveCareerDirection(
            CompletedAnalysisSnapshot analysis,
            String profileHeadline,
            List<BestFitRole> bestFitRoles) {
        if (analysis != null && analysis.targetRole() != null && !analysis.targetRole().isBlank()) {
            return analysis.targetRole().trim();
        }
        if (bestFitRoles != null && !bestFitRoles.isEmpty() && bestFitRoles.get(0).matchPercentage() >= 35) {
            return bestFitRoles.get(0).roleTitle();
        }
        if (profileHeadline != null && !profileHeadline.isBlank() && profileHeadline.length() <= 80) {
            return profileHeadline.trim();
        }
        if (bestFitRoles != null && !bestFitRoles.isEmpty()) {
            return bestFitRoles.get(0).roleTitle();
        }
        return "Software Engineer";
    }

    public int calculateSnapshotMatch(
            CompletedAnalysisSnapshot analysis,
            String careerDirection,
            List<BestFitRole> bestFitRoles) {
        if (analysis == null) {
            return 0;
        }
        int roleMatch = 0;
        if (analysis.jdMatchScore() != null) {
            roleMatch = analysis.jdMatchScore();
        } else if (bestFitRoles != null && !bestFitRoles.isEmpty()) {
            roleMatch = bestFitRoles.stream()
                .filter(r -> r.roleTitle().equalsIgnoreCase(careerDirection))
                .mapToInt(BestFitRole::matchPercentage)
                .findFirst()
                .orElse(bestFitRoles.get(0).matchPercentage());
        } else {
            roleMatch = analysis.overallScore();
        }

        double blended = roleMatch * 0.60 + analysis.overallScore() * 0.40;
        return Math.min(99, Math.max(10, (int) Math.round(blended)));
    }

    public int calculatePotentialLift(
            CompletedAnalysisSnapshot analysis,
            int matchPercentage,
            List<String> topMissingSkills) {
        if (analysis == null || matchPercentage >= 96) {
            return 0;
        }
        int skillGapLift = Math.min(14, (topMissingSkills != null ? topMissingSkills.size() : 0) * 3);
        int ruleHeadroom = Math.max(0, 95 - analysis.overallScore()) / 3;
        int rawLift = Math.max(3, skillGapLift + ruleHeadroom);
        int ceiling = Math.max(0, 97 - matchPercentage);
        return Math.min(rawLift, ceiling);
    }

    public BiggestOpportunity buildBiggestOpportunity(
            CompletedAnalysisSnapshot analysis,
            String careerDirection,
            int matchPercentage,
            int potentialLift,
            List<BestFitRole> bestFitRoles) {
        if (analysis == null) {
            return new BiggestOpportunity(
                "Upload & analyze your resume",
                "Run your first ATS scan to uncover missing keywords, formatting gaps, and instant score lift opportunities.",
                0,
                List.of(),
                "NONE",
                "Analyze Resume",
                "/resume-analysis"
            );
        }

        List<String> missingRoleSkills = resolveMissingSkillsForDirection(analysis, careerDirection, bestFitRoles);
        String topWeakRule = findTopMissingRule(analysis.scoreDetails());
        int lift = Math.max(1, Math.min(potentialLift, Math.max(4, (int) Math.round(potentialLift * 0.85))));

        if (!missingRoleSkills.isEmpty()) {
            List<String> topSkills = missingRoleSkills.stream().limit(4).toList();
            String joined = formatSkillList(topSkills);
            String desc = "Add " + joined + ". One project or achievement bullet covering these closes the gaps recruiters flag most often for " + careerDirection + " roles.";
            return new BiggestOpportunity(
                "Biggest opportunity",
                desc,
                lift,
                topSkills,
                topWeakRule,
                "View skill gaps",
                "/skill-gap"
            );
        }

        String firstImprovement = (analysis.areasToImprove() != null && !analysis.areasToImprove().isEmpty())
            ? analysis.areasToImprove().get(0)
            : "Add more quantified impact metrics (%, $, latency, user scale) to your experience bullets.";

        return new BiggestOpportunity(
            "Biggest opportunity",
            firstImprovement,
            Math.min(lift, Math.max(1, 97 - matchPercentage)),
            List.of(),
            topWeakRule,
            "Optimize resume",
            "/resume-builder"
        );
    }

    public List<String> buildProfileHighlights(
            CompletedAnalysisSnapshot analysis,
            List<BestFitRole> bestFitRoles) {
        if (analysis == null) {
            return List.of("Upload your PDF or DOCX resume to unlock personalized career highlights");
        }

        List<String> highlights = new ArrayList<>();
        if (bestFitRoles != null && !bestFitRoles.isEmpty()) {
            BestFitRole top = bestFitRoles.get(0);
            List<String> matched = top.matchedSkills();
            if (matched.size() >= 2) {
                highlights.add("Strong " + matched.get(0) + " & " + matched.get(1) + " foundation");
            } else if (matched.size() == 1) {
                highlights.add("Strong " + matched.get(0) + " foundation");
            }
            if (matched.size() >= 3) {
                highlights.add(matched.get(2) + " hands-on experience");
            }
            String domainLabel = CareerRoleCatalog.ROLES.stream()
                .filter(r -> r.title().equals(top.roleTitle()))
                .map(RoleDefinition::domainLabel)
                .findFirst()
                .orElse("target technical roles");
            if (top.matchPercentage() >= 55) {
                highlights.add("Good fit for " + domainLabel + " roles");
            }
        }

        if (analysis.strengths() != null) {
            for (String s : analysis.strengths()) {
                if (highlights.size() >= 4) break;
                if (s != null && !s.isBlank() && highlights.stream().noneMatch(h -> h.equalsIgnoreCase(s))) {
                    highlights.add(s);
                }
            }
        }

        return highlights.stream().limit(4).toList();
    }

    public List<RecommendedMove> buildRecommendedMoves(
            CompletedAnalysisSnapshot analysis,
            String careerDirection,
            List<BestFitRole> bestFitRoles,
            BiggestOpportunity opportunity) {
        if (analysis == null) {
            return List.of(
                new RecommendedMove("analyze-resume", "Upload & Analyze Resume", "Get instant ATS score, skill gaps, and role match", "/resume-analysis"),
                new RecommendedMove("resume-builder", "Build ATS Resume", "Start with a clean ATS-friendly resume structure", "/resume-builder")
            );
        }

        List<RecommendedMove> moves = new ArrayList<>();
        int gapCount = opportunity != null && opportunity.missingSkills() != null ? opportunity.missingSkills().size() : 0;
        moves.add(new RecommendedMove(
            "skill-gap",
            "Go to Skill Gap",
            gapCount > 0
                ? "Close " + gapCount + " priority gaps for " + careerDirection
                : "See exactly what to improve",
            "/skill-gap"
        ));

        moves.add(new RecommendedMove(
            "career-targets",
            "Go to Career Targets",
            bestFitRoles != null && !bestFitRoles.isEmpty()
                ? "See your best job direction (" + bestFitRoles.get(0).roleTitle() + ")"
                : "See your best job direction",
            "/career-targets"
        ));

        if (analysis.overallScore() < 80 || analysis.atsScore() < 80 || analysis.formattingScore() < 78) {
            moves.add(new RecommendedMove(
                "resume-builder",
                "Optimize in Resume Builder",
                "Lift your " + analysis.atsScore() + "% ATS compatibility and formatting score",
                "/resume-builder"
            ));
        }

        return moves;
    }

    private BestFitRole evaluateRole(
            RoleDefinition role,
            String lowerText,
            String skillsSection,
            String experienceSection,
            String titleContext,
            Set<String> canonicalKeywords,
            String dominantDomain,
            int overallScore) {
        List<String> matched = new ArrayList<>();
        List<String> missingCore = new ArrayList<>();

        double coreScoreSum = 0.0;
        for (String skill : role.coreSkills()) {
            double presence = measureSkillPresence(skill, lowerText, skillsSection, experienceSection, canonicalKeywords);
            if (presence > 0.0) {
                matched.add(DomainKeywordDictionary.displayName(skill));
                coreScoreSum += presence;
            } else {
                missingCore.add(DomainKeywordDictionary.displayName(skill));
            }
        }

        double supportScoreSum = 0.0;
        for (String skill : role.supportingSkills()) {
            double presence = measureSkillPresence(skill, lowerText, skillsSection, experienceSection, canonicalKeywords);
            if (presence > 0.0) {
                matched.add(DomainKeywordDictionary.displayName(skill));
                supportScoreSum += presence;
            }
        }

        // Concave normalization: meeting 5 core skills represents strong readiness without requiring exhaustive lists
        double coreTarget = Math.min(5.0, Math.max(1.0, role.coreSkills().size()));
        double supportTarget = Math.min(4.0, Math.max(1.0, role.supportingSkills().size()));
        double coreRatio = Math.min(1.0, coreScoreSum / coreTarget);
        double supportRatio = Math.min(1.0, supportScoreSum / supportTarget);
        double titleBonus = matchesTitleContext(role.title(), titleContext) ? 1.0 : 0.0;
        double domainBonus = role.domain().equals(dominantDomain) ? 1.0 : 0.0;
        double qualityFactor = Math.min(1.0, overallScore / 85.0);

        double rawPct = (Math.pow(coreRatio, 0.85) * 58.0)
            + (supportRatio * 17.0)
            + (titleBonus * 10.0)
            + (domainBonus * 7.0)
            + (qualityFactor * 8.0);
        if (coreScoreSum == 0.0) {
            rawPct = Math.min(rawPct, 24.0);
        }
        int matchPct = Math.min(96, Math.max(8, (int) Math.round(rawPct)));

        return new BestFitRole(
            role.title(),
            role.domain(),
            matchPct,
            matched.stream().distinct().limit(8).toList(),
            missingCore.stream().limit(6).toList()
        );
    }

    private double measureSkillPresence(
            String canonicalSkill,
            String lowerText,
            String skillsSection,
            String experienceSection,
            Set<String> canonicalKeywords) {
        boolean found = canonicalKeywords.contains(canonicalSkill) || containsTerm(lowerText, canonicalSkill);
        if (!found) {
            for (var alias : DomainKeywordDictionary.ALIAS_TO_CANONICAL.entrySet()) {
                if (alias.getValue().equals(canonicalSkill) && containsTerm(lowerText, alias.getKey())) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) return 0.0;

        boolean inExp = !experienceSection.isBlank() && containsTerm(experienceSection, canonicalSkill);
        boolean inSkills = !skillsSection.isBlank() && containsTerm(skillsSection, canonicalSkill);
        if (inExp && inSkills) return 1.0;
        if (inExp || inSkills) return 0.92;
        return 0.80;
    }

    private boolean containsTerm(String text, String term) {
        if (text == null || text.isBlank() || term == null || term.isBlank()) return false;
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])");
        return p.matcher(text).find();
    }

    private boolean matchesTitleContext(String roleTitle, String titleContext) {
        if (titleContext == null || titleContext.isBlank()) return false;
        String cleanRole = roleTitle.toLowerCase(Locale.ROOT).replace("/", " ");
        if (titleContext.contains(cleanRole)) return true;
        String[] parts = cleanRole.split("\\s+");
        int hits = 0;
        for (String part : parts) {
            if (part.length() > 3 && !part.equals("engineer") && !part.equals("developer") && !part.equals("manager")) {
                if (titleContext.contains(part)) hits++;
            }
        }
        return hits >= 1;
    }

    private boolean matchesPreferredTitle(String candidateTitle, String preferred) {
        if (preferred == null || preferred.isBlank()) return false;
        return candidateTitle.equalsIgnoreCase(preferred.trim());
    }

    private List<String> resolveMissingSkillsForDirection(
            CompletedAnalysisSnapshot analysis,
            String careerDirection,
            List<BestFitRole> bestFitRoles) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        if (analysis.missingKeywords() != null) {
            missing.addAll(analysis.missingKeywords());
        }
        if (bestFitRoles != null) {
            bestFitRoles.stream()
                .filter(r -> r.roleTitle().equalsIgnoreCase(careerDirection))
                .findFirst()
                .or(() -> bestFitRoles.stream().findFirst())
                .ifPresent(r -> missing.addAll(r.missingCoreSkills()));
        }
        return new ArrayList<>(missing);
    }

    @SuppressWarnings("unchecked")
    private String findTopMissingRule(Map<String, Object> scoreDetails) {
        if (scoreDetails == null || scoreDetails.isEmpty()) return "NONE";
        String worstRule = "NONE";
        int largestGap = 0;

        for (String key : List.of("ats", "content", "formatting")) {
            Object breakdownObj = scoreDetails.get(key);
            if (!(breakdownObj instanceof Map<?, ?> breakdown)) continue;
            Object detailsObj = breakdown.get("details");
            if (!(detailsObj instanceof List<?> details)) continue;

            for (Object item : details) {
                if (!(item instanceof Map<?, ?> d)) continue;
                Object maxObj = d.get("pointsMax");
                Object earnedObj = d.get("pointsEarned");
                Object ruleObj = d.get("rule");
                if (maxObj instanceof Number maxN && earnedObj instanceof Number earnedN && ruleObj != null) {
                    int gap = maxN.intValue() - earnedN.intValue();
                    if (gap > largestGap) {
                        largestGap = gap;
                        worstRule = ruleObj.toString();
                    }
                }
            }
        }
        return worstRule;
    }

    private Set<String> canonicalSet(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return Set.of();
        Set<String> set = new HashSet<>();
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank()) {
                set.add(DomainKeywordDictionary.canonicalize(kw));
            }
        }
        return set;
    }

    private String sectionText(Map<String, String> sections, String key) {
        if (sections == null) return "";
        String val = sections.get(key);
        return val != null ? val.toLowerCase(Locale.ROOT) : "";
    }

    private String firstLines(String text, int maxLines) {
        if (text == null || text.isBlank()) return "";
        return text.lines().limit(maxLines).reduce((a, b) -> a + " " + b).orElse("");
    }

    private String formatSkillList(List<String> skills) {
        if (skills.isEmpty()) return "";
        if (skills.size() == 1) return skills.get(0);
        if (skills.size() == 2) return skills.get(0) + " & " + skills.get(1);
        return String.join(", ", skills.subList(0, skills.size() - 1)) + " & " + skills.get(skills.size() - 1);
    }
}
