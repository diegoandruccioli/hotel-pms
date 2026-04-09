package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.PaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Data Transfer Object for creating a Payment.
 *
 * @param amount               the payment amount
 * @param paymentMethod        the method used for the payment
 * @param transactionReference external reference for the transaction
 */
public record PaymentRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 10, fraction = 2,
                message = "Amount must have at most 10 integer digits and 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Payment method is required") PaymentMethod paymentMethod,

        @NotBlank(message = "Transaction reference is required")
        @Size(max = 100, message = "Transaction reference must not exceed 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9\\-_/\\.]+$",
                message = "Transaction reference contains invalid characters")
        String transactionReference) {
}
