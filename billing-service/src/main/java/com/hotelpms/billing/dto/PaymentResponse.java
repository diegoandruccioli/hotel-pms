package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object representing a Payment response.
 *
 * @param id                   the payment UUID
 * @param paymentDate          the date the payment was recorded
 * @param amount               the payment amount
 * @param paymentMethod        the method used for the payment
 * @param transactionReference the external reference for the transaction
 * @param invoiceId            the associated invoice UUID
 */
public record PaymentResponse(
        UUID id,
        LocalDateTime paymentDate,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String transactionReference,
        UUID invoiceId) {
}
