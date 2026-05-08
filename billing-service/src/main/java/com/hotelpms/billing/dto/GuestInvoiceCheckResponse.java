package com.hotelpms.billing.dto;

import java.time.LocalDate;

/**
 * Response carrying the last invoice date for a guest within a hotel.
 * Used by the guest-service GDPR legal-hold guard to verify the fiscal
 * retention obligation (Codice Civile art. 2220 — ten-year minimum).
 *
 * @param hasInvoices     {@code true} if the guest has at least one invoice
 * @param lastInvoiceDate the most recent invoice issue date, or {@code null}
 *                        if {@code hasInvoices} is {@code false}
 */
public record GuestInvoiceCheckResponse(boolean hasInvoices, LocalDate lastInvoiceDate) {
}
