package com.opticareer.app.modules.analysis.scoring;

import java.util.HashSet;
import java.util.Set;

public final class ActionVerbDictionary {
    private ActionVerbDictionary() {}

    public static final Set<String> LEADERSHIP = Set.of(
        "led", "managed", "directed", "oversaw", "supervised", "coordinated",
        "mentored", "coached", "guided", "headed", "chaired", "orchestrated",
        "spearheaded", "pioneered", "championed", "steered", "mobilized",
        "delegated", "empowered", "inspired", "cultivated", "established"
    );

    public static final Set<String> ACHIEVEMENT = Set.of(
        "achieved", "accomplished", "attained", "exceeded", "surpassed",
        "outperformed", "delivered", "earned", "won", "secured", "captured",
        "generated", "produced", "yielded", "maximized", "doubled", "tripled",
        "improved", "increased", "boosted", "elevated", "enhanced",
        "strengthened", "expanded", "grew", "accelerated", "advanced"
    );

    public static final Set<String> TECHNICAL = Set.of(
        "developed", "engineered", "designed", "architected", "built",
        "implemented", "deployed", "automated", "coded", "programmed",
        "configured", "integrated", "migrated", "optimized", "refactored",
        "debugged", "tested", "maintained", "upgraded", "modernized",
        "containerized", "provisioned", "scaled", "benchmarked"
    );

    public static final Set<String> ANALYTICAL = Set.of(
        "analyzed", "assessed", "evaluated", "investigated", "researched",
        "diagnosed", "identified", "measured", "quantified", "calculated",
        "forecasted", "projected", "modeled", "mapped", "audited",
        "surveyed", "examined", "reviewed", "validated"
    );

    public static final Set<String> COMMUNICATION = Set.of(
        "presented", "authored", "drafted", "published", "documented",
        "communicated", "reported", "briefed", "negotiated", "collaborated",
        "facilitated", "mediated", "translated", "clarified", "articulated",
        "advocated", "persuaded", "influenced", "liaised", "partnered"
    );

    public static final Set<String> CREATIVE = Set.of(
        "created", "innovated", "conceptualized", "formulated",
        "devised", "invented", "launched", "initiated", "introduced",
        "revamped", "transformed", "reimagined", "redesigned", "customized",
        "tailored", "prototyped", "brainstormed", "crafted", "shaped"
    );

    public static final Set<String> ORGANIZATIONAL = Set.of(
        "organized", "planned", "scheduled", "structured", "streamlined",
        "systematized", "standardized", "consolidated", "centralized",
        "simplified", "prioritized", "allocated", "budgeted", "administered",
        "processed", "executed", "monitored", "tracked"
    );

    public static final Set<String> ALL_VERBS;

    static {
        var all = new HashSet<String>();
        all.addAll(LEADERSHIP);
        all.addAll(ACHIEVEMENT);
        all.addAll(TECHNICAL);
        all.addAll(ANALYTICAL);
        all.addAll(COMMUNICATION);
        all.addAll(CREATIVE);
        all.addAll(ORGANIZATIONAL);
        ALL_VERBS = Set.copyOf(all);
    }

    public static boolean isActionVerb(String word) {
        return ALL_VERBS.contains(word.toLowerCase());
    }
}
