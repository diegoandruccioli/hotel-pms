package com.hotelpms.frontdesk.reservations.dto;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.validation.ValidDateRange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for Reservation.
 *
 * <p>The {@link ValidDateRange} class-level constraint ensures that
 * {@code checkOutDate} is strictly after {@code checkInDate}. A zero-night or
 * inverted date range would otherwise pass individual field validation and could
 * corrupt the overlap-detection query (T-RES-03).
 *
 * @param guestId        the guest id
 * @param expectedGuests the expected number of guests
 * @param checkInDate    the check in date
 * @param checkOutDate   the check out date
 * @param status         the status
 * @param lineItems      the line items
 * @param version        the version the client last read, for optimistic-lock
 *                       conflict detection on update; {@code null} skips the
 *                       check (backward-compatible for callers that don't send
 *                       it yet, and ignored entirely on create)
 */
@ValidDateRange
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record ReservationRequest(
                @NotNull(message = "Required") UUID guestId,
                @NotNull(message = "Required") Integer expectedGuests,

                @NotNull(message = "Required") @FutureOrPresent(message = "Future") LocalDate checkInDate,

                @NotNull(message = "Required") @FutureOrPresent(message = "Future") LocalDate checkOutDate,

                @NotNull(message = "Required") ReservationStatus status,

                @NotEmpty(message = "Required")
                @Size(max = MAX_LINE_ITEMS, message = "Too many rooms") @Valid List<ReservationLineItemRequest> lineItems,

                Long version) {

        /**
         * Upper bound on rooms in a single reservation (Finding #18,
         * security-report.md — LOW) — comfortably covers a large group/event
         * booking for a PMS-scale hotel.
         */
        static final int MAX_LINE_ITEMS = 50;

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
