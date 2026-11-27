package com.opticareer.app.modules.analysis.domain;

import java.util.List;

public record AiSuggestion(
    List<String> enhancedStrengths,
    List<String> enhancedImprovements,
    List<String> keywordGaps,
    List<BulletRewrite> bulletRewrites
) {
    public record BulletRewrite(String original, String suggested, String reason) {}
}
