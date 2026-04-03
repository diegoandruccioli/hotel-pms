package com.hotelpms.stay.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight DTO representing the invoice status check during check-out.
 *
 * @param id            the invoice UUID
 * @param reservationId the reservation UUID
 * @param status        the invoice status string (e.g. PAID, ISSUED, CANCELLED)
 * @param totalAmount   the total amount billed
 */
public record InvoiceStatusResponse(
        UUID id,
        UUID reservationId,
        String status,
        BigDecimal totalAmount) {
}
