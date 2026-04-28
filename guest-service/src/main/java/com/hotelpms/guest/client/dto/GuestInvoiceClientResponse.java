package com.hotelpms.guest.client.dto;

import java.time.LocalDate;

/**
 * Client-side mirror of the billing-service {@code GuestInvoiceCheckResponse}.
 * Decouples the guest-service from a compile-time dependency on the
 * billing-service module.
 *
 * @param hasInvoices     {@code true} if the guest has at least one invoice
 * @param lastInvoiceDate most recent invoice issue date, or {@code null}
 */
public record GuestInvoiceClientResponse(boolean hasInvoices, LocalDate lastInvoiceDate) {
}
