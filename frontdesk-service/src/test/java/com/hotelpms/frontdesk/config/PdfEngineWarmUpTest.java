package com.hotelpms.frontdesk.config;

import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PdfEngineWarmUpTest {

    @Test
    @DisplayName("warms up the engine once when enabled")
    void shouldWarmUpWhenEnabled() {
        final PdfTemplateRenderer renderer = mock(PdfTemplateRenderer.class);

        new PdfEngineWarmUp(renderer, true).warmUp();

        verify(renderer).warmUp();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void shouldNotWarmUpWhenDisabled() {
        final PdfTemplateRenderer renderer = mock(PdfTemplateRenderer.class);

        new PdfEngineWarmUp(renderer, false).warmUp();

        verify(renderer, never()).warmUp();
    }
}
