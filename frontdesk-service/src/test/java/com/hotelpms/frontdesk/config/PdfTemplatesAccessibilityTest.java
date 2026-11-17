package com.hotelpms.frontdesk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PDF engine always produces PDF/UA documents, which require a document description:
 * openhtmltopdf takes it from {@code <meta name="description">} and otherwise logs
 * "Document will not be PDF/UA compliant" on every render.
 */
class PdfTemplatesAccessibilityTest {

    private static final String TEMPLATES_DIR = "templates/pdf";
    private static final String HTML_SUFFIX = ".html";
    private static final String DESCRIPTION_META = "<meta name=\"description\"";

    @Test
    @DisplayName("every PDF template declares a document description")
    void everyPdfTemplateDeclaresADescription() throws IOException, URISyntaxException {
        final URL dir = getClass().getClassLoader().getResource(TEMPLATES_DIR);
        assertThat(dir).as("templates/pdf must be on the classpath").isNotNull();

        final List<Path> templates;
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            templates = files.filter(path -> path.toString().endsWith(HTML_SUFFIX)).toList();
        }

        assertThat(templates).as("at least one PDF template").isNotEmpty();
        for (final Path template : templates) {
            assertThat(Files.readString(template, StandardCharsets.UTF_8))
                    .as("%s must contain <meta name=\"description\"> (PDF/UA)", template)
                    .contains(DESCRIPTION_META);
        }
    }
}
