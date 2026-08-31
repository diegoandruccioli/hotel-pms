package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.stays.domain.Stay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the invoice belonging to a stay, and whether it's still open — shared by
 * {@link StayBillingCoordinator} (check-out) and {@code
 * com.hotelpms.frontdesk.citytax.service.impl.CityTaxAssessmentServiceImpl} (rectification
 * and backfill, which must never post a charge against a fiscal document that's already
 * closed). Split out on its own, depending on nothing but {@link BillingClient}, precisely
 * so both callers can depend on it without a cycle — {@code StayBillingCoordinator} itself
 * depends on {@code CityTaxAssessmentService}, so {@code CityTaxAssessmentServiceImpl}
 * cannot depend back on {@code StayBillingCoordinator}.
 */
@Component
@RequiredArgsConstructor
public class StayInvoiceResolver {

    private static final String OPEN_INVOICE_STATUS = "ISSUED";

    private final BillingClient billingClient;

    /**
     * Resolves the invoice to verify for a stay. Reservation-based stays are looked up
     * by {@code reservationId}; walk-in stays (always {@code null} reservationId) are
     * looked up by the {@code invoiceId} stored on the stay at check-in time. A walk-in
     * whose invoice was never created (billing-service was unavailable at check-in) has
     * no {@code invoiceId} to look up — returns {@code null}.
     *
     * @param stay the stay
     * @return the invoice status response, or {@code null} if it cannot be resolved
     */
    public InvoiceStatusResponse resolve(final Stay stay) {
        if (stay.getReservationId() != null) {
            return billingClient.getLatestInvoiceByReservation(stay.getReservationId());
        }
        if (stay.getInvoiceId() != null) {
            return billingClient.getInvoiceById(stay.getInvoiceId());
        }
        return null;
    }

    /**
     * Whether the stay's invoice is currently open ({@code ISSUED}) — the only status
     * that can still receive new charges or have one removed.
     *
     * @param stay the stay
     * @return {@code true} if the invoice resolves and is open
     */
    public boolean isOpen(final Stay stay) {
        final InvoiceStatusResponse invoice = resolve(stay);
        return invoice != null && OPEN_INVOICE_STATUS.equalsIgnoreCase(invoice.status());
    }
}
