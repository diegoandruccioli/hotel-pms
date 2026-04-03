package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Data Transfer Object for creating a Payment.
 *
 * @param amount               the payment amount
 * @param paymentMethod        the method used for the payment
 * @param transactionReference external reference for the transaction
 */
public record PaymentRequest(
        @NotNull(message = "Amount is required") @Positive(message = "Amount must be positive") BigDecimal amount,

        @NotNull(message = "Payment method is required") PaymentMethod paymentMethod,

        @NotBlank(message = "Transaction reference is required") String transactionReference) {
}
