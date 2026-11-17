package com.hotelpms.frontdesk.config;

import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Warms up the shared PDF engine once the application is ready, so the first quotation or
 * housekeeping PDF requested by an operator does not pay the cold-start cost (measured at
 * ~2.7 s against ~0.3 s once warm). The warm-up itself lives in {@code pdf-template-engine}
 * ({@link PdfTemplateRenderer#warmUp()}): it renders a bundled document, so nothing here
 * knows about frontdesk templates and nothing touches the database.
 */
@Slf4j
@Component
public class PdfEngineWarmUp {

    private final PdfTemplateRenderer pdfTemplateRenderer;
    private final boolean enabled;

    /**
     * Creates the warm-up component.
     *
     * @param pdfTemplateRenderer the shared PDF renderer bean
     * @param enabled             whether to warm up at startup
     *                            ({@code pdf.warm-up.enabled}, default {@code true})
     */
    public PdfEngineWarmUp(final PdfTemplateRenderer pdfTemplateRenderer,
            @Value("${pdf.warm-up.enabled:true}") final boolean enabled) {
        this.pdfTemplateRenderer = pdfTemplateRenderer;
        this.enabled = enabled;
    }

    /**
     * Runs the engine warm-up after the application context is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            log.info("[PDF-WARMUP] skipped (pdf.warm-up.enabled=false)");
            return;
        }
        pdfTemplateRenderer.warmUp();
    }
}
