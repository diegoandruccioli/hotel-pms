package com.hotelpms.billing.dto;

/**
 * A single invoice search result (C12): the invoice itself plus the guest's display
 * name, resolved separately via a cross-service call to guest-service (Invoice only
 * stores a guestId — see {@code InvoiceServiceImpl.searchInvoices}). Kept as a wrapper
 * rather than a field on {@link InvoiceResponse} itself, since that record is
 * constructed by MapStruct and by many other call sites where a guest name was never
 * resolved.
 *
 * @param invoice   the invoice data
 * @param guestName the guest's display name, or {@code null} if not resolved
 *                  (guest-service unavailable, or the guest no longer exists)
 */
public record InvoiceSearchResultResponse(InvoiceResponse invoice, String guestName) {
}
