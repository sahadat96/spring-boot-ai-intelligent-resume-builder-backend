package com.opticareer.app.modules.analysis.domain;

import java.util.List;
import java.util.Set;

/**
 * Physical layout signals extracted directly from PDF glyphs, fonts, coordinates, and annotations.
 * Null or empty defaults are used for non-PDF / scanned documents.
 */
public record PdfLayoutMetadata(
    Set<String> fontNames,
    float bodyFontSizePt,
    float maxHeadingFontSizePt,
    int multiColumnLineCount,
    int embeddedImageCount,
    List<String> annotationUrls,
    double garbledCharRatio
) {
    public static PdfLayoutMetadata empty() {
        return new PdfLayoutMetadata(Set.of(), 0f, 0f, 0, 0, List.of(), 0.0);
    }

    public boolean hasHeadingSizeHierarchy() {
        return bodyFontSizePt > 0f && maxHeadingFontSizePt >= bodyFontSizePt + 1.5f;
    }

    public boolean hasMultiColumnLayout() {
        return multiColumnLineCount >= 6;
    }
}
