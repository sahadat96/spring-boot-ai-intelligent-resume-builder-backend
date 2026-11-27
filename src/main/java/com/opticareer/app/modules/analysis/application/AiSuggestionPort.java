package com.opticareer.app.modules.analysis.application;

import com.opticareer.app.modules.analysis.domain.AiSuggestion;
import com.opticareer.app.modules.analysis.domain.AnalysisResult;
import com.opticareer.app.modules.analysis.domain.ParsedDocument;
import java.util.UUID;

/**
 * Port interface for post-analysis AI suggestion enhancement (strictly for subscribed/premium users).
 * Scanning, parsing, and scoring always remain deterministic and algorithmic.
 */
public interface AiSuggestionPort {

    record EnhancedOutput(AiSuggestion suggestion, String provider, String model) {}

    EnhancedOutput enhance(
        UUID ownerId,
        UUID analysisId,
        ParsedDocument document,
        AnalysisResult result,
        String targetRole,
        String jobDescription
    );
}
