package com.hotelpms.pdftemplate;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Default {@link PdfTemplateRenderer}: Thymeleaf resolves classpath HTML templates,
 * openhtmltopdf renders the resulting markup to a PDF/UA (ISO 14289) accessible,
 * tagged A4 PDF.
 *
 * <p>PDF/UA is always on, not an opt-in flag: it costs nothing extra from the
 * caller (the embedded font ships with this module) and there is no good reason
 * for a generated business document to be inaccessible by default.
 *
 * <p>PDF/UA forbids relying on the renderer's built-in (non-embedded) fonts, so a
 * real font — Noto Sans, SIL Open Font License 1.1, see {@code fonts/OFL.txt} —
 * is bundled and registered for both weights used by templates (400 regular,
 * 700 bold). Templates must reference it via {@code font-family: 'Noto Sans'}.
 */
@Slf4j
public class ThymeleafPdfTemplateRenderer implements PdfTemplateRenderer {

    /** The family name templates must use in CSS ({@code font-family: 'Noto Sans'}). */
    public static final String FONT_FAMILY = "Noto Sans";

    private static final String CHARSET = "UTF-8";
    private static final String FONT_REGULAR_RESOURCE = "fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD_RESOURCE = "fonts/NotoSans-Bold.ttf";
    private static final int WEIGHT_REGULAR = 400;
    private static final int WEIGHT_BOLD = 700;
    private static final boolean SUBSET_FONT = true;

    private final TemplateEngine templateEngine;

    /**
     * Creates a renderer resolving templates under the given classpath prefix.
     *
     * @param templateClasspathPrefix the classpath prefix templates are resolved
     *                                from (e.g. {@code "templates/pdf/"}); {@code .html}
     *                                is appended automatically
     */
    public ThymeleafPdfTemplateRenderer(final String templateClasspathPrefix) {
        final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(templateClasspathPrefix);
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(CHARSET);
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    /** {@inheritDoc} */
    @Override
    public byte[] render(final String templateName, final Map<String, Object> context) {
        final Context thymeleafContext = new Context();
        thymeleafContext.setVariables(context);
        final String html = templateEngine.process(templateName, thymeleafContext);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Fast mode has been the default renderer since openhtmltopdf 1.0.5 —
            // no need to request it explicitly (the method is now deprecated).
            new PdfRendererBuilder()
                    .usePdfUaAccessibility(true)
                    .useFont(() -> fontStream(FONT_REGULAR_RESOURCE), FONT_FAMILY,
                            WEIGHT_REGULAR, FontStyle.NORMAL, SUBSET_FONT)
                    .useFont(() -> fontStream(FONT_BOLD_RESOURCE), FONT_FAMILY,
                            WEIGHT_BOLD, FontStyle.NORMAL, SUBSET_FONT)
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            log.info("[PDF-TEMPLATE] rendered | template={} | bytes={}", templateName, out.size());
            return out.toByteArray();
        } catch (final IOException e) {
            throw new PdfRenderException("PDF_RENDER_FAILED: " + templateName, e);
        }
    }

    private InputStream fontStream(final String classpathResource) {
        final InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource);
        if (in == null) {
            throw new IllegalStateException("PDF_FONT_ASSET_MISSING: " + classpathResource);
        }
        return in;
    }
}
