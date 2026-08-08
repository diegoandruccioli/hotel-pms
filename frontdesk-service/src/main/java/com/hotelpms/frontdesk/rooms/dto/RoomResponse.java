package com.hotelpms.frontdesk.rooms.dto;

import java.io.Serializable;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Room.
 *
 * @param id                 the id
 * @param hotelId            the hotel id
 * @param roomNumber         the room number
 * @param roomType           the room type
 * @param status             the room status
 * @param active             is active
 * @param createdAt          creation time
 * @param updatedAt          update time
 * @param resolvedTotalPrice the total price for a specific stay, resolved via
 *                           {@code RatePricingService}; {@code null} except when
 *                           this response comes from the availability search
 *                           ({@code GET /rooms/availability}), the one endpoint
 *                           that knows the stay's dates
 */
public record RoomResponse(
        UUID id,
        UUID hotelId,
        String roomNumber,
        RoomTypeResponse roomType,
        RoomStatus status,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal resolvedTotalPrice) implements Serializable {

    /**
     * Returns a copy of this response with {@code resolvedTotalPrice} set — used
     * by the availability search to attach a date-aware price without a
     * separate response shape.
     *
     * @param price the resolved total price for the requested stay
     * @return a copy of this response carrying the resolved price
     */
    public RoomResponse withResolvedTotalPrice(final BigDecimal price) {
        return new RoomResponse(id, hotelId, roomNumber, roomType, status, active, createdAt, updatedAt, price);
    }
}
