package com.hotelpms.billing.config;

import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import com.hotelpms.pdftemplate.ThymeleafPdfTemplateRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the domain-agnostic {@code pdf-template-engine} module into billing-service.
 * Templates are resolved from {@code src/main/resources/templates/pdf/}.
 */
@Configuration
public class PdfTemplateConfig {

    private static final String TEMPLATE_CLASSPATH_PREFIX = "templates/pdf/";

    /**
     * Creates the PDF template renderer used by {@code PdfInvoiceServiceImpl}.
     *
     * @return a Thymeleaf-backed {@link PdfTemplateRenderer}
     */
    @Bean
    public PdfTemplateRenderer pdfTemplateRenderer() {
        return new ThymeleafPdfTemplateRenderer(TEMPLATE_CLASSPATH_PREFIX);
    }
}
