package com.hotelpms.billing.config;

import com.hotelpms.billing.service.impl.PdfInvoiceServiceImpl;
import com.hotelpms.pdftemplate.PdfRenderException;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Renders a throw-away invoice and receipt once the application is ready, so the first
 * real PDF request does not pay the cold-start cost (class loading and JIT warm-up of
 * Thymeleaf, openhtmltopdf and PDFBox, measured at ~6 s on a fresh container against
 * ~0.9 s once warm).
 *
 * <p>Uses static dummy data only: no database, no Feign call, nothing is stored. A
 * failure is logged and swallowed — warming up must never prevent the service from
 * starting.
 */
@Slf4j
@Component
public class PdfRendererWarmUp {

    private static final String WARM_UP_NUMBER = "WARMUP";
    private static final String WARM_UP_TEXT = "Warm-up";
    private static final String WARM_UP_AMOUNT = "EUR 0,00";
    private static final String WARM_UP_DATE = "01/01/2000";

    private final PdfTemplateRenderer pdfTemplateRenderer;
    private final boolean enabled;

    /**
     * Creates the warm-up component.
     *
     * @param pdfTemplateRenderer the renderer used by {@code PdfInvoiceServiceImpl}
     * @param enabled             whether to warm up at startup
     *                            ({@code billing.pdf.warm-up.enabled}, default {@code true})
     */
    public PdfRendererWarmUp(final PdfTemplateRenderer pdfTemplateRenderer,
            @Value("${billing.pdf.warm-up.enabled:true}") final boolean enabled) {
        this.pdfTemplateRenderer = pdfTemplateRenderer;
        this.enabled = enabled;
    }

    /**
     * Renders both invoice templates once with dummy data.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            log.info("[PDF-WARMUP] skipped (billing.pdf.warm-up.enabled=false)");
            return;
        }
        final long start = System.nanoTime();
        try {
            for (final String template : List.of(
                    PdfInvoiceServiceImpl.TEMPLATE_FATTURA, PdfInvoiceServiceImpl.TEMPLATE_RICEVUTA)) {
                pdfTemplateRenderer.render(template, dummyContext());
            }
            log.info("[PDF-WARMUP] done | templates=2 | ms={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } catch (final PdfRenderException e) {
            log.warn("[PDF-WARMUP] failed — the first real PDF will be slow", e);
        }
    }

    /**
     * Builds a context holding every key the invoice templates read.
     *
     * @return a fresh, mutable context map
     */
    static Map<String, Object> dummyContext() {
        final Map<String, Object> context = new HashMap<>();
        context.put("logoDataUri", null);
        context.put("hotelName", WARM_UP_TEXT);
        context.put("hotelAddress", WARM_UP_TEXT);
        context.put("hotelVat", null);
        context.put("hotelFiscalCode", null);
        context.put("docTitle", WARM_UP_TEXT);
        context.put("invoiceNumber", WARM_UP_NUMBER);
        context.put("issueDate", WARM_UP_DATE);
        context.put("status", WARM_UP_TEXT);
        context.put("guestDisplayName", WARM_UP_TEXT);
        context.put("guestPersonalName", null);
        context.put("guestFiscalCode", null);
        context.put("guestVat", null);
        context.put("guestPec", null);
        context.put("charges", List.of(Map.of(
                "typeLabel", WARM_UP_TEXT,
                "description", WARM_UP_TEXT,
                "vatLabel", WARM_UP_AMOUNT,
                "amountFormatted", WARM_UP_AMOUNT)));
        context.put("payments", List.of(Map.of(
                "methodLabel", WARM_UP_TEXT,
                "dateFormatted", WARM_UP_DATE,
                "reference", WARM_UP_TEXT,
                "amountFormatted", WARM_UP_AMOUNT)));
        context.put("vatBreakdown", List.of(Map.of(
                "rateLabel", WARM_UP_TEXT,
                "natura", "false",
                "taxableFormatted", WARM_UP_AMOUNT,
                "vatFormatted", WARM_UP_AMOUNT)));
        context.put("totalFormatted", WARM_UP_AMOUNT);
        context.put("dueFormatted", WARM_UP_AMOUNT);
        return context;
    }
}
