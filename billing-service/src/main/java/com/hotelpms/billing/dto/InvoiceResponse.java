package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object representing an Invoice response.
 *
 * @param id            the invoice UUID
 * @param hotelId       the hotel identifier
 * @param invoiceNumber the auto-generated invoice number
 * @param issueDate     the date the invoice was issued
 * @param totalAmount   the total amount of the invoice
 * @param status        the current status of the invoice
 * @param reservationId the associated reservation UUID
 * @param guestId       the associated guest UUID
 * @param payments      the list of payments made against this invoice
 */
public record InvoiceResponse(
        UUID id,
        UUID hotelId,
        String invoiceNumber,
        LocalDateTime issueDate,
        BigDecimal totalAmount,
        InvoiceStatus status,
        UUID reservationId,
        UUID guestId,
        List<PaymentResponse> payments) {

    /**
     * Compact constructor to ensure defensive copying of mutable lists.
     *
     * @param id            the invoice UUID
     * @param hotelId       the hotel identifier
     * @param invoiceNumber the auto-generated invoice number
     * @param issueDate     the date the invoice was issued
     * @param totalAmount   the total amount of the invoice
     * @param status        the current status of the invoice
     * @param reservationId the associated reservation UUID
     * @param guestId       the associated guest UUID
     * @param payments      the list of payments made against this invoice
     */
    public InvoiceResponse {
        payments = payments == null ? List.of() : List.copyOf(payments);
    }
}
