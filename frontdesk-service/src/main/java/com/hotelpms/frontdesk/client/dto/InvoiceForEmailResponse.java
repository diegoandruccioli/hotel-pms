package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Richer invoice projection used when building checkout summary emails.
 * Fetched from the same {@code GET /api/v1/invoices/{id}} endpoint as
 * {@link InvoiceStatusResponse}; Jackson ignores the extra fields returned
 * by billing-service that are not in {@link InvoiceStatusResponse}.
 *
 * @param id            invoice UUID
 * @param reservationId associated reservation UUID (may be null for walk-ins)
 * @param invoiceNumber human-readable invoice number
 * @param status        lifecycle status (e.g. PAID, ISSUED)
 * @param totalAmount   total billed amount
 * @param currency      ISO 4217 currency code (e.g. EUR)
 * @param charges       ordered list of individual charge lines
 */
public record InvoiceForEmailResponse(
        UUID id,
        UUID reservationId,
        String invoiceNumber,
        String status,
        BigDecimal totalAmount,
        String currency,
        List<ChargeLineDto> charges) {

    /**
     * Compact constructor to ensure defensive copying of the charges list.
     */
    public InvoiceForEmailResponse {
        charges = charges == null ? null : List.copyOf(charges);
    }

    /**
     * Returns a copy of the charges list to prevent external modification.
     *
     * @return the invoice charge lines
     */
    @Override
    public List<ChargeLineDto> charges() {
        return charges == null ? null : List.copyOf(charges);
    }
}
