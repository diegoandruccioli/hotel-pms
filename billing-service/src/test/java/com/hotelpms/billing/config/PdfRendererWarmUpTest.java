package com.hotelpms.billing.config;

import com.hotelpms.billing.service.impl.PdfInvoiceServiceImpl;
import com.hotelpms.pdftemplate.PdfRenderException;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import com.hotelpms.pdftemplate.ThymeleafPdfTemplateRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfRendererWarmUpTest {

    private static final String TEMPLATE_PREFIX = "templates/pdf/";
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final int MAGIC_LENGTH = 4;

    @Test
    @DisplayName("renders both invoice templates once when enabled")
    void shouldRenderBothTemplatesWhenEnabled() {
        final PdfTemplateRenderer renderer = mock(PdfTemplateRenderer.class);

        new PdfRendererWarmUp(renderer, true).warmUp();

        verify(renderer).render(eq(PdfInvoiceServiceImpl.TEMPLATE_FATTURA), anyMap());
        verify(renderer).render(eq(PdfInvoiceServiceImpl.TEMPLATE_RICEVUTA), anyMap());
        verify(renderer, times(2)).render(anyString(), anyMap());
    }

    @Test
    @DisplayName("does not render anything when disabled")
    void shouldNotRenderWhenDisabled() {
        final PdfTemplateRenderer renderer = mock(PdfTemplateRenderer.class);

        new PdfRendererWarmUp(renderer, false).warmUp();

        verify(renderer, never()).render(anyString(), anyMap());
    }

    @Test
    @DisplayName("swallows a render failure so startup is never blocked")
    void shouldSwallowRenderFailure() {
        final PdfTemplateRenderer renderer = mock(PdfTemplateRenderer.class);
        when(renderer.render(anyString(), anyMap()))
                .thenThrow(new PdfRenderException("boom", new IOException("boom")));

        assertThatCode(() -> new PdfRendererWarmUp(renderer, true).warmUp()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dummy context renders a real PDF with both production templates")
    void shouldRenderRealPdfsFromDummyContext() {
        final PdfTemplateRenderer renderer = new ThymeleafPdfTemplateRenderer(TEMPLATE_PREFIX);

        for (final String template : new String[] {
                PdfInvoiceServiceImpl.TEMPLATE_FATTURA, PdfInvoiceServiceImpl.TEMPLATE_RICEVUTA}) {
            final Map<String, Object> context = PdfRendererWarmUp.dummyContext();
            final byte[] pdf = renderer.render(template, context);
            assertThat(pdf).startsWith(PDF_MAGIC);
            assertThat(pdf.length).isGreaterThan(MAGIC_LENGTH);
        }
    }
}
