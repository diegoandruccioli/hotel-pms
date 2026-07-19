package com.hotelpms.pdftemplate;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Default {@link PdfTemplateRenderer}: Thymeleaf resolves classpath HTML templates,
 * openhtmltopdf renders the resulting markup to PDF/A4.
 */
@Slf4j
public class ThymeleafPdfTemplateRenderer implements PdfTemplateRenderer {

    private static final String CHARSET = "UTF-8";

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
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            log.info("[PDF-TEMPLATE] rendered | template={} | bytes={}", templateName, out.size());
            return out.toByteArray();
        } catch (final IOException e) {
            throw new PdfRenderException("PDF_RENDER_FAILED: " + templateName, e);
        }
    }
}
