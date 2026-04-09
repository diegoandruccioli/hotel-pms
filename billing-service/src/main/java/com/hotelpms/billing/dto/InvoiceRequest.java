package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.InvoiceStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data Transfer Object for creating or updating an Invoice.
 *
 * @param hotelId       the hotel identifier for multi-tenancy
 * @param reservationId the associated reservation UUID
 * @param guestId       the associated guest UUID
 * @param totalAmount   the total amount of the invoice
 * @param status        the status of the invoice
 */
public record InvoiceRequest(
        UUID hotelId,

        @NotNull(message = "Reservation ID is required") UUID reservationId,

        @NotNull(message = "Guest ID is required") UUID guestId,

        @NotNull(message = "Amount required")
        @PositiveOrZero(message = "Must be >= 0")
        @Digits(integer = 10, fraction = 2,
                message = "Amount must have at most 10 integer digits and 2 decimal places")
        BigDecimal totalAmount,

        @NotNull(message = "Status is required") InvoiceStatus status) {
}
