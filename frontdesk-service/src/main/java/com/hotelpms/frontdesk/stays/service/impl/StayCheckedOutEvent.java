package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;

import java.util.UUID;

/**
 * Published inside the check-out transaction, handled only after it commits by
 * {@link StayCheckoutEmailListener}: carries just what the email step needs, so the
 * listener re-reads the stay instead of holding a detached entity.
 *
 * @param stayId  the stay that was checked out
 * @param hotelId the owning hotel
 * @param invoice the invoice verified as settled during check-out
 */
public record StayCheckedOutEvent(UUID stayId, UUID hotelId, InvoiceStatusResponse invoice) {
}
