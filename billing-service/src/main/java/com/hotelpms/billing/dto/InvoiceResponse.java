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
 * @param totalAmount   the current total amount (sum of all charges)
 * @param status        the current status of the invoice
 * @param reservationId the associated reservation UUID
 * @param guestId       the associated guest UUID
 * @param stayId        the associated stay UUID (null for manually created invoices)
 * @param payments      the list of payments made against this invoice
 * @param charges       the list of line-item charges on this invoice
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
        UUID stayId,
        List<PaymentResponse> payments,
        List<ChargeResponse> charges) {

    /**
     * Compact constructor ensuring defensive copying of mutable lists.
     *
     * @param id            the invoice UUID
     * @param hotelId       the hotel identifier
     * @param invoiceNumber the auto-generated invoice number
     * @param issueDate     the date the invoice was issued
     * @param totalAmount   the current total amount
     * @param status        the current status of the invoice
     * @param reservationId the associated reservation UUID
     * @param guestId       the associated guest UUID
     * @param stayId        the associated stay UUID
     * @param payments      the list of payments made against this invoice
     * @param charges       the list of line-item charges on this invoice
     */
    public InvoiceResponse {
        payments = payments == null ? List.of() : List.copyOf(payments);
        charges = charges == null ? List.of() : List.copyOf(charges);
    }
}
