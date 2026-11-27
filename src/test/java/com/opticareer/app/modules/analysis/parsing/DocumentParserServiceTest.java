package com.opticareer.app.modules.analysis.parsing;

import com.opticareer.app.modules.analysis.domain.ParsedDocument;
import com.opticareer.app.modules.analysis.domain.ResumeSection;
import com.opticareer.app.shared.error.DomainException;
import com.opticareer.app.shared.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentParserServiceTest {

    private final DocumentParserService parser = new DocumentParserService();

    @Test
    void parseRealGeneratedPdf_extractsTextSectionsBulletsAndLayoutMetadata() throws Exception {
        byte[] pdfBytes = createSampleResumePdf();

        ParsedDocument doc = parser.parse(pdfBytes, "application/pdf");

        assertThat(doc.ocrUsed()).isFalse();
        assertThat(doc.pageCount()).isEqualTo(1);
        assertThat(doc.rawText()).contains("Sahadat Hossain", "sahadat@opticareer.co");
        assertThat(doc.hasSection(ResumeSection.CONTACT)).isTrue();
        assertThat(doc.hasSection(ResumeSection.SUMMARY)).isTrue();
        assertThat(doc.hasSection(ResumeSection.EXPERIENCE)).isTrue();
        assertThat(doc.hasSection(ResumeSection.SKILLS)).isTrue();
        assertThat(doc.bulletPoints()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(doc.layoutMetadata().fontNames()).isNotEmpty();
        assertThat(doc.layoutMetadata().hasHeadingSizeHierarchy()).isTrue();
    }

    @Test
    void validateMagicBytes_rejectsSpoofedPdfPayload() {
        byte[] fakeBytes = "NOT_A_REAL_PDF_FILE".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(fakeBytes, "application/pdf"))
            .isInstanceOf(DomainException.class)
            .extracting(e -> ((DomainException) e).code())
            .isEqualTo(ErrorCode.UNSUPPORTED_FORMAT);
    }

    @Test
    void parseRtfDocumentViaTika_extractsTextAndBullets() {
        String rtf = "{\\rtf1\\ansi Sahadat Hossain\\par sahadat@opticareer.co\\par "
            + "EXPERIENCE\\par - Architected Spring Boot REST APIs serving 50K users\\par "
            + "SKILLS\\par Java, Spring Boot, PostgreSQL, Docker}";
        ParsedDocument doc = parser.parse(rtf.getBytes(StandardCharsets.UTF_8), "application/rtf");

        assertThat(doc.ocrUsed()).isFalse();
        assertThat(doc.rawText()).contains("Sahadat Hossain", "Spring Boot");
        assertThat(doc.hasSection(ResumeSection.EXPERIENCE)).isTrue();
        assertThat(doc.hasSection(ResumeSection.SKILLS)).isTrue();
    }

    private byte[] createSampleResumePdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                cs.beginText();
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 740);

                cs.setFont(bold, 16);
                cs.showText("Sahadat Hossain");
                cs.newLine();

                cs.setFont(regular, 10.5f);
                cs.showText("sahadat@opticareer.co | +880 1711-234567 | github.com/sahadat");
                cs.newLine();
                cs.newLine();

                cs.setFont(bold, 13);
                cs.showText("PROFESSIONAL SUMMARY");
                cs.newLine();
                cs.setFont(regular, 10.5f);
                cs.showText("Backend engineer specializing in Java, Spring Boot, PostgreSQL, and Docker.");
                cs.newLine();
                cs.newLine();

                cs.setFont(bold, 13);
                cs.showText("WORK EXPERIENCE");
                cs.newLine();
                cs.setFont(regular, 10.5f);
                cs.showText("Software Engineer | OptiCareer | Jan 2023 - Present");
                cs.newLine();
                cs.showText("- Architected Spring Boot REST APIs serving 50K+ users with 99.9% uptime");
                cs.newLine();
                cs.showText("- Reduced database query latency by 45% using PostgreSQL indexing and Redis caching");
                cs.newLine();
                cs.newLine();

                cs.setFont(bold, 13);
                cs.showText("SKILLS");
                cs.newLine();
                cs.setFont(regular, 10.5f);
                cs.showText("Java, Spring Boot, PostgreSQL, Redis, Docker, TypeScript, React, Next.js");

                cs.endText();
            }

            pdf.save(baos);
            return baos.toByteArray();
        }
    }
}
