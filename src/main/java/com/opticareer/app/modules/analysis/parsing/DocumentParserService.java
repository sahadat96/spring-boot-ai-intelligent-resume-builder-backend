package com.opticareer.app.modules.analysis.parsing;

import com.opticareer.app.modules.analysis.domain.ParsedDocument;
import com.opticareer.app.modules.analysis.domain.PdfLayoutMetadata;
import com.opticareer.app.modules.analysis.domain.ResumeSection;
import com.opticareer.app.modules.analysis.scoring.SectionHeaderPatterns;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);
    private static final String PARSER_VERSION = "2.0.0";
    private static final int MIN_TEXT_CHARS = 50;

    public ParsedDocument parse(byte[] data, String mimeType) {
        long start = System.nanoTime();
        Objects.requireNonNull(data, "File data must not be null");
        Objects.requireNonNull(mimeType, "MIME type must not be null");
        validateMagicBytes(data, mimeType);

        String text;
        int pageCount = 1;
        boolean ocrUsed = false;
        PdfLayoutMetadata layoutMetadata = PdfLayoutMetadata.empty();

        if (mimeType.equals("application/pdf")) {
            var pdfResult = extractPdfWithLayout(data);
            text = pdfResult.text;
            pageCount = pdfResult.pages;
            layoutMetadata = pdfResult.layout;

            if (text.trim().length() < MIN_TEXT_CHARS || layoutMetadata.garbledCharRatio() > 0.25) {
                log.info("PDF text extraction yielded {} chars (garbledRatio={}), attempting OCR",
                    text.length(), String.format("%.2f", layoutMetadata.garbledCharRatio()));
                try {
                    String ocrText = extractWithOcr(data, true);
                    if (ocrText != null && ocrText.trim().length() > text.trim().length()) {
                        text = ocrText;
                        ocrUsed = true;
                    }
                } catch (Exception e) {
                    log.warn("OCR fallback failed: {}", e.getMessage());
                }
            }
        } else if (mimeType.startsWith("image/")) {
            text = extractWithOcr(data, false);
            ocrUsed = true;
        } else {
            text = extractWithTika(data);
        }

        text = normalizeText(text);

        Map<ResumeSection, String> sections = extractSections(text);
        if (sections.isEmpty() || (sections.size() == 1 && sections.containsKey(ResumeSection.UNKNOWN))) {
            sections = inferSections(text);
        }

        List<String> bullets = extractBulletPoints(text);

        long elapsed = Math.max(1L, (System.nanoTime() - start) / 1_000_000);
        return new ParsedDocument(text, sections, bullets, pageCount, ocrUsed, PARSER_VERSION, elapsed, layoutMetadata);
    }

    /**
     * Validates file magic-byte signatures against declared MIME type to prevent spoofed uploads.
     */
    public void validateMagicBytes(byte[] data, String mimeType) {
        if (data.length < 4) {
            throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Uploaded file is too small or corrupted.");
        }
        switch (mimeType) {
            case "application/pdf" -> {
                String head = new String(data, 0, Math.min(data.length, 8), StandardCharsets.US_ASCII);
                if (!head.contains("%PDF-")) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid PDF file signature.");
                }
            }
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.oasis.opendocument.text" -> {
                // ZIP archive header: PK\x03\x04
                if (data[0] != 0x50 || data[1] != 0x4B || data[2] != 0x03 || data[3] != 0x04) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid DOCX/ODT archive signature.");
                }
            }
            case "application/msword" -> {
                // OLE2 Compound Document header: D0 CF 11 E0
                if ((data[0] & 0xFF) != 0xD0 || (data[1] & 0xFF) != 0xCF
                    || (data[2] & 0xFF) != 0x11 || (data[3] & 0xFF) != 0xE0) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid DOC file signature.");
                }
            }
            case "application/rtf", "text/rtf" -> {
                String head = new String(data, 0, Math.min(data.length, 6), StandardCharsets.US_ASCII);
                if (!head.startsWith("{\\rtf")) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid RTF file signature.");
                }
            }
            case "image/png", "image/x-png" -> {
                if ((data[0] & 0xFF) != 0x89 || data[1] != 0x50 || data[2] != 0x4E || data[3] != 0x47) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid PNG file signature.");
                }
            }
            case "image/jpeg", "image/jpg", "image/pjpeg" -> {
                if ((data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8 || (data[2] & 0xFF) != 0xFF) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid JPEG file signature.");
                }
            }
            case "image/tiff" -> {
                boolean le = data[0] == 'I' && data[1] == 'I' && data[2] == 0x2A && data[3] == 0x00;
                boolean be = data[0] == 'M' && data[1] == 'M' && data[2] == 0x00 && data[3] == 0x2A;
                if (!le && !be) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Invalid TIFF file signature.");
                }
            }
            default -> {
                throw new DomainException(ErrorCode.UNSUPPORTED_FORMAT, "Unsupported MIME type: " + mimeType);
            }
        }
    }

    private PdfExtractResult extractPdfWithLayout(byte[] data) {
        try (PDDocument doc = Loader.loadPDF(data)) {
            LayoutAwarePdfStripper stripper = new LayoutAwarePdfStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);

            int imageCount = 0;
            List<String> urls = new ArrayList<>();
            for (PDPage page : doc.getPages()) {
                PDResources resources = page.getResources();
                if (resources != null) {
                    for (COSName name : resources.getXObjectNames()) {
                        try {
                            if (resources.getXObject(name) instanceof PDImageXObject) {
                                imageCount++;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI uri) {
                        if (uri.getURI() != null && !uri.getURI().isBlank()) {
                            urls.add(uri.getURI());
                        }
                    }
                }
            }

            PdfLayoutMetadata layout = stripper.buildMetadata(imageCount, urls, text != null ? text : "");
            return new PdfExtractResult(text != null ? text : "", doc.getNumberOfPages(), layout);
        } catch (Exception e) {
            log.warn("PDFBox extraction failed: {}", e.getMessage());
            return new PdfExtractResult("", 1, PdfLayoutMetadata.empty());
        }
    }

    private record PdfExtractResult(String text, int pages, PdfLayoutMetadata layout) {}

    private static final class LayoutAwarePdfStripper extends PDFTextStripper {
        private final Set<String> fontNames = new LinkedHashSet<>();
        private final Map<Integer, Integer> roundedSizeFreq = new HashMap<>();
        private float maxFontSize = 0f;
        private int multiColumnLines = 0;

        LayoutAwarePdfStripper() {}

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            super.writeString(text, textPositions);
            if (textPositions == null || textPositions.isEmpty()) return;

            float prevEndX = -1f;
            int gutterSplitIndex = -1;
            int largeHorizontalGaps = 0;

            for (int i = 0; i < textPositions.size(); i++) {
                TextPosition tp = textPositions.get(i);
                if (tp.getFont() != null && tp.getFont().getName() != null) {
                    String rawName = tp.getFont().getName();
                    int plusIdx = rawName.indexOf('+');
                    fontNames.add(plusIdx >= 0 ? rawName.substring(plusIdx + 1) : rawName);
                }
                float size = tp.getFontSizeInPt();
                if (size > 3f && size < 72f) {
                    roundedSizeFreq.merge(Math.round(size), 1, Integer::sum);
                    if (size > maxFontSize) maxFontSize = size;
                }
                float x = tp.getXDirAdj();
                float w = tp.getWidthDirAdj();
                if (prevEndX > 0f && (x - prevEndX) > 75f) {
                    largeHorizontalGaps++;
                    if (gutterSplitIndex < 0) {
                        gutterSplitIndex = i;
                    }
                }
                prevEndX = Math.max(prevEndX, x + w);
            }
            // Ignore right-aligned date/location headers (e.g. "Software Engineer    Jan 2022 - Present")
            if (largeHorizontalGaps >= 1 && text.trim().length() > 35 && gutterSplitIndex > 0) {
                String trimmed = text.trim();
                boolean looksLikeRightAlignedDate = TextAnalyzer.countDates(trimmed) >= 1 && textPositions.size() < 60;
                if (!looksLikeRightAlignedDate) {
                    multiColumnLines++;
                }
            }
        }

        PdfLayoutMetadata buildMetadata(int imageCount, List<String> urls, String text) {
            float bodySize = roundedSizeFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> (float) e.getKey())
                .orElse(0f);

            double garbledRatio = 0.0;
            if (!text.isEmpty()) {
                long garbled = text.chars()
                    .filter(c -> c == '\uFFFD' || (c < 32 && c != '\n' && c != '\r' && c != '\t'))
                    .count();
                garbledRatio = (double) garbled / text.length();
            }

            return new PdfLayoutMetadata(
                Set.copyOf(fontNames),
                bodySize,
                maxFontSize,
                multiColumnLines,
                imageCount,
                List.copyOf(urls),
                garbledRatio
            );
        }
    }

    /**
     * Extracts text from scanned PDFs or images. Uses Tess4J first and transparently falls back to
     * system `tesseract` CLI (essential on Alpine Linux musl environments).
     */
    private String extractWithOcr(byte[] data, boolean isPdf) {
        try {
            List<BufferedImage> pagesToOcr = renderImages(data, isPdf);
            if (pagesToOcr.isEmpty()) {
                throw new IllegalArgumentException("Could not decode image data for OCR.");
            }
            try {
                return runTess4j(pagesToOcr);
            } catch (Throwable jnaOrNativeError) {
                log.info("Tess4J unavailable ({}), falling back to system tesseract CLI", jnaOrNativeError.getMessage());
                return runTesseractCli(pagesToOcr);
            }
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException(ErrorCode.ANALYSIS_FAILED, "OCR extraction failed: " + e.getMessage());
        }
    }

    private List<BufferedImage> renderImages(byte[] data, boolean isPdf) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        if (!isPdf) {
            try (InputStream is = new ByteArrayInputStream(data)) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    images.add(img);
                    return images;
                }
            }
        }
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int limit = Math.min(doc.getNumberOfPages(), 8);
            for (int i = 0; i < limit; i++) {
                images.add(renderer.renderImageWithDPI(i, 300));
            }
        }
        return images;
    }

    private String runTess4j(List<BufferedImage> images) throws Exception {
        net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();
        tesseract.setLanguage("eng");
        String dataPath = System.getenv("TESSDATA_PREFIX");
        if (dataPath != null && !dataPath.isBlank()) {
            tesseract.setDatapath(dataPath);
        }
        StringBuilder sb = new StringBuilder();
        for (BufferedImage image : images) {
            sb.append(tesseract.doOCR(image)).append("\n");
        }
        return sb.toString();
    }

    private String runTesseractCli(List<BufferedImage> images) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        for (BufferedImage image : images) {
            Path tempImg = Files.createTempFile("opticareer-ocr-", ".png");
            try {
                ImageIO.write(image, "png", tempImg.toFile());
                ProcessBuilder pb = new ProcessBuilder("tesseract", tempImg.toString(), "stdout", "-l", "eng");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("Tesseract CLI timed out.");
                }
                if (process.exitValue() != 0) {
                    throw new IOException("Tesseract CLI exited with code " + process.exitValue());
                }
                sb.append(output).append("\n");
            } finally {
                Files.deleteIfExists(tempImg);
            }
        }
        return sb.toString();
    }

    private String extractWithTika(byte[] data) {
        try {
            Tika tika = new Tika();
            tika.setMaxStringLength(500_000);
            return tika.parseToString(new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new DomainException(ErrorCode.ANALYSIS_FAILED, "Failed to extract text from document: " + e.getMessage());
        }
    }

    Map<ResumeSection, String> extractSections(String text) {
        Map<ResumeSection, String> sections = new LinkedHashMap<>();
        String[] lines = text.split("\n");
        ResumeSection currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        // Capture implicit header block before first detected section as CONTACT
        StringBuilder preamble = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (currentSection != null && currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                continue;
            }

            ResumeSection detected = detectSection(trimmed);
            if (detected != null) {
                if (currentSection == null && preamble.length() > 0) {
                    sections.put(ResumeSection.CONTACT, preamble.toString().trim());
                } else if (currentSection != null && currentContent.length() > 0) {
                    sections.merge(currentSection, currentContent.toString().trim(), (a, b) -> a + "\n" + b);
                }
                currentSection = detected;
                currentContent = new StringBuilder();
            } else if (currentSection == null) {
                preamble.append(trimmed).append("\n");
            } else {
                currentContent.append(trimmed).append("\n");
            }
        }

        if (currentSection != null && currentContent.length() > 0) {
            sections.merge(currentSection, currentContent.toString().trim(), (a, b) -> a + "\n" + b);
        }

        return sections;
    }

    private ResumeSection detectSection(String line) {
        if (line.length() > 60) return null;
        for (var entry : SectionHeaderPatterns.patterns().entrySet()) {
            if (entry.getKey().matcher(line).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    Map<ResumeSection, String> inferSections(String text) {
        Map<ResumeSection, String> sections = new LinkedHashMap<>();
        String[] lines = text.split("\n");

        StringBuilder contactInfo = new StringBuilder();
        int contactEnd = Math.min(5, lines.length);
        for (int i = 0; i < contactEnd; i++) {
            contactInfo.append(lines[i].trim()).append("\n");
        }
        if (contactInfo.length() > 0) {
            sections.put(ResumeSection.CONTACT, contactInfo.toString().trim());
        }

        if (lines.length > contactEnd) {
            StringBuilder rest = new StringBuilder();
            for (int i = contactEnd; i < lines.length; i++) {
                rest.append(lines[i]).append("\n");
            }
            sections.put(ResumeSection.UNKNOWN, rest.toString().trim());
        }

        return sections;
    }

    List<String> extractBulletPoints(String text) {
        List<String> bullets = new ArrayList<>();
        // Symbol bullets (•, ▪, ►, etc.) may appear with 0+ spaces in PDFBox output; ASCII (-, *, >, 1.) require whitespace
        Pattern bulletPattern = Pattern.compile(
            "^\\s*(?:[\u2022\u25AA\u25BA\u25CF\u25CB\u25A0\u2192\u27A4]\\s*|(?:[\\-*>]|\\d+[.)])\\s+)(.+)$",
            Pattern.MULTILINE
        );
        var matcher = bulletPattern.matcher(text);
        while (matcher.find()) {
            String bullet = matcher.group(1).trim();
            if (!bullet.isEmpty() && bullet.length() > 10) {
                bullets.add(bullet);
            }
        }
        return bullets;
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        text = text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        text = text.replaceAll("[\t ]+", " ");
        text = text.replaceAll("\n{3,}", "\n\n");
        text = text.replace("\u00a0", " ");
        text = text.replace("\ufeff", "");
        return text.trim();
    }
}
