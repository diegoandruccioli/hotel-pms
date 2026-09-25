package com.hotelpms.frontdesk.client.dto;

import java.time.LocalDate;

/**
 * Response carrying the last invoice date relevant to a stay, as reported by
 * billing-service. Used by the GDPR legal-hold guard (E22) to verify the
 * Codice Civile art. 2220 ten-year fiscal retention obligation before
 * anonymising a {@code StayGuest} record.
 *
 * @param hasInvoices     {@code true} if the stay has at least one relevant
 *                        invoice
 * @param lastInvoiceDate the most recent relevant invoice's issue date, or
 *                        {@code null} if {@code hasInvoices} is {@code false}
 */
public record StayInvoiceDateResponse(boolean hasInvoices, LocalDate lastInvoiceDate) {
}
