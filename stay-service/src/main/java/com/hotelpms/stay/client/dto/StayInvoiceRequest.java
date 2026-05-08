package com.hotelpms.stay.client.dto;

import java.util.UUID;

/**
 * Request DTO sent to billing-service to create an invoice at check-in.
 *
 * @param stayId        the stay UUID (logical reference)
 * @param guestId       the primary guest UUID
 * @param reservationId the reservation UUID
 */
public record StayInvoiceRequest(UUID stayId, UUID guestId, UUID reservationId) {
}
