package com.hotelpms.reservation.dto;

import com.hotelpms.reservation.domain.ReservationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for Reservation.
 *
 * @param guestId      the guest id
 * @param expectedGuests the expected number of guests
 * @param checkInDate  the check in date
 * @param checkOutDate the check out date
 * @param status       the status
 * @param lineItems    the line items
 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record ReservationRequest(
                @NotNull(message = "Required") UUID guestId,
                @NotNull(message = "Required") Integer expectedGuests,

                @NotNull(message = "Required") @FutureOrPresent(message = "Future") LocalDate checkInDate,

                @NotNull(message = "Required") @FutureOrPresent(message = "Future") LocalDate checkOutDate,

                @NotNull(message = "Required") ReservationStatus status,

                @NotEmpty(message = "Required") @Valid List<ReservationLineItemRequest> lineItems) {

        /**
         * Compact constructor.
         */
        public ReservationRequest {
                if (lineItems != null) {
                        lineItems = List.copyOf(lineItems);
                }
        }

        /**
         * Getter for line items.
         * 
         * @return the line items
         */
        @Override
        public List<ReservationLineItemRequest> lineItems() {
                return lineItems == null ? null : List.copyOf(lineItems);
        }
}
