package com.opticareer.app.modules.analysis.domain;

public record ScoreDetail(
    String rule,
    String description,
    int pointsEarned,
    int pointsMax,
    boolean passed
) {
    public static ScoreDetail pass(String rule, String description, int points) {
        return new ScoreDetail(rule, description, points, points, true);
    }

    public static ScoreDetail fail(String rule, String description, int max) {
        return new ScoreDetail(rule, description, 0, max, false);
    }

    public static ScoreDetail partial(String rule, String description, int earned, int max) {
        return new ScoreDetail(rule, description, earned, max, earned > 0);
    }
}
