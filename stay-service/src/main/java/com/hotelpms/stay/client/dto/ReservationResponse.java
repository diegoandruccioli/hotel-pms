package com.hotelpms.stay.client.dto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object representing a Reservation from the Reservation Service.
 *
 * @param id        the reservation ID
 * @param guestId   the associated guest ID
 * @param roomId    the associated room ID
 * @param status    the reservation status
 * @param lineItems the reservation line items
 */
public record ReservationResponse(
        UUID id,
        UUID guestId,
        UUID roomId,
        String status,
        List<ReservationLineItemResponse> lineItems) {

    /**
     * Canonical constructor that defensively copies {@code lineItems} to prevent
     * SpotBugs EI/EI2 (exposure of internal mutable state).
     *
     * @param id        the reservation ID
     * @param guestId   the associated guest ID
     * @param roomId    the associated room ID
     * @param status    the reservation status
     * @param lineItems the reservation line items (may be null)
     */
    public ReservationResponse(
            final UUID id,
            final UUID guestId,
            final UUID roomId,
            final String status,
            final List<ReservationLineItemResponse> lineItems) {
        this.id = id;
        this.guestId = guestId;
        this.roomId = roomId;
        this.status = status;
        this.lineItems = lineItems == null ? null : Collections.unmodifiableList(lineItems);
    }
}
