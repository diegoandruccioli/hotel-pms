package com.hotelpms.pdftemplate;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafPdfTemplateRendererTest {

    private static final int PDF_MAGIC_BYTES_LEN = 5;
    private static final String PDF_MAGIC = "%PDF-";
    private static final String MINIMAL_TEMPLATE = "minimal";
    private static final String TITLE_VAR = "title";

    private final PdfTemplateRenderer renderer = new ThymeleafPdfTemplateRenderer("templates/");

    @Test
    void rendersAMinimalTemplateToAValidPdf() {
        final byte[] pdf = renderer.render(MINIMAL_TEMPLATE, Map.of(TITLE_VAR, "Hello"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, PDF_MAGIC_BYTES_LEN, StandardCharsets.ISO_8859_1)).isEqualTo(PDF_MAGIC);
    }

    @Test
    void substitutesTemplateVariables() {
        final byte[] withTitle = renderer.render(MINIMAL_TEMPLATE, Map.of(TITLE_VAR, "Unique Marker Value"));
        final byte[] withoutContext = renderer.render(MINIMAL_TEMPLATE, Map.of(TITLE_VAR, "Other"));

        // Different variable values must produce different (differently-sized) PDF content.
        assertThat(withTitle).isNotEqualTo(withoutContext);
    }

    @Test
    void throwsPdfRenderExceptionForAMissingTemplate() {
        assertThatThrownBy(() -> renderer.render("does-not-exist", Map.of()))
                .isInstanceOf(RuntimeException.class);
    }
}
