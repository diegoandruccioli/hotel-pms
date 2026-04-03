package com.hotelpms.reservation.dto;

import com.hotelpms.reservation.domain.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Reservation.
 *
 * @param id           the id
 * @param guestId        unique ID of the guest
 * @param guestFullName  full name of the guest
 * @param expectedGuests the expected number of guests
 * @param actualGuests the current number of checked-in guests
 * @param checkInDate  the check in date
 * @param checkOutDate the check out date
 * @param status       the status
 * @param lineItems    the line items
 * @param active       is active
 * @param createdAt    creation time
 * @param updatedAt    update time
 */
@SuppressWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record ReservationResponse(
                UUID id,
                UUID guestId,
                String guestFullName,
                Integer expectedGuests,
                Integer actualGuests,
                LocalDate checkInDate,
                LocalDate checkOutDate,
                ReservationStatus status,
                List<ReservationLineItemResponse> lineItems,
                boolean active,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) {

        /**
         * Compact constructor.
         */
        public ReservationResponse {
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
        public List<ReservationLineItemResponse> lineItems() {
                return lineItems == null ? null : List.copyOf(lineItems);
        }
}
