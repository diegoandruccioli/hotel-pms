package com.hotelpms.guest.client;

import com.hotelpms.guest.exception.ExportSourceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * The GDPR export must fail rather than hand the data subject a file with a silently
 * empty stay or invoice history: both history fallbacks have to throw.
 */
class HistoryFallbackTest {

    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void invoiceHistoryFallbackFailsTheExport() {
        final BillingServiceClient client = mock(BillingServiceClient.class, CALLS_REAL_METHODS);
        final RuntimeException cause = new IllegalStateException("billing down");

        final ExportSourceUnavailableException ex = assertThrows(ExportSourceUnavailableException.class,
                () -> client.invoiceHistoryFallback(GUEST_ID, cause));

        assertEquals("invoices", ex.getSection());
        assertSame(cause, ex.getCause());
    }

    @Test
    void stayHistoryFallbackFailsTheExport() {
        final StayServiceClient client = mock(StayServiceClient.class, CALLS_REAL_METHODS);
        final RuntimeException cause = new IllegalStateException("frontdesk down");

        final ExportSourceUnavailableException ex = assertThrows(ExportSourceUnavailableException.class,
                () -> client.stayHistoryFallback(GUEST_ID, cause));

        assertEquals("stays", ex.getSection());
        assertSame(cause, ex.getCause());
    }
}
