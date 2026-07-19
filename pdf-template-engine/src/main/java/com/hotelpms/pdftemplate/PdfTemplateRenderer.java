package com.hotelpms.pdftemplate;

import java.util.Map;

/**
 * Renders a named HTML/CSS template into a PDF document.
 *
 * <p>Deliberately domain-agnostic: this module knows nothing about invoices,
 * hotels, or any other business concept — callers build the {@code context}
 * map and own the template files. Images must be embedded as {@code data:}
 * URIs in the rendered HTML (e.g. {@code <img th:src="${logoDataUri}">}) —
 * this renderer never resolves file paths or network URLs, which keeps it
 * free of filesystem/SSRF assumptions and portable across projects.
 */
@FunctionalInterface
public interface PdfTemplateRenderer {

    /**
     * Renders the named template with the given variables into a PDF.
     *
     * @param templateName the template name, resolved by the configured
     *                     template resolver (e.g. classpath {@code .html} file,
     *                     without extension)
     * @param context      the variables made available to the template
     * @return the rendered PDF as bytes
     * @throws PdfRenderException if template processing or PDF rendering fails
     */
    byte[] render(String templateName, Map<String, Object> context);
}
