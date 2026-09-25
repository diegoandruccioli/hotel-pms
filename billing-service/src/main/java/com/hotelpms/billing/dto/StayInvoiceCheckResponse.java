package com.hotelpms.billing.dto;

import java.time.LocalDate;

/**
 * Response carrying the last invoice date relevant to a stay within a hotel.
 * Used by the frontdesk-service GDPR legal-hold guard (E22) to verify the
 * Codice Civile art. 2220 ten-year fiscal retention obligation before
 * anonymising a {@code StayGuest} record.
 *
 * @param hasInvoices     {@code true} if the stay has at least one relevant
 *                        invoice — either its own folio or a group MASTER
 *                        folio holding a charge routed from this stay
 * @param lastInvoiceDate the most recent relevant invoice's issue date, or
 *                        {@code null} if {@code hasInvoices} is {@code false}
 */
public record StayInvoiceCheckResponse(boolean hasInvoices, LocalDate lastInvoiceDate) {
}
