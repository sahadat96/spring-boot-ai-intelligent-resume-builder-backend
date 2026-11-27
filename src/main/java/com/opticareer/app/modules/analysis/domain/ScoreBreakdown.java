package com.opticareer.app.modules.analysis.domain;

import java.util.List;

public record ScoreBreakdown(
    String category,
    int score,
    List<ScoreDetail> details
) {
    public static ScoreBreakdown of(String category, List<ScoreDetail> details) {
        int total = details.stream().mapToInt(ScoreDetail::pointsEarned).sum();
        int max = details.stream().mapToInt(ScoreDetail::pointsMax).sum();
        int score = max > 0 ? Math.round((total * 100f) / max) : 0;
        return new ScoreBreakdown(category, Math.min(score, 100), details);
    }
}
