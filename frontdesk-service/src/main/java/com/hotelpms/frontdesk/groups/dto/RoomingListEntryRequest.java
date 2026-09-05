package com.hotelpms.frontdesk.groups.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One row of a group's rooming list: a single room assigned to a single guest,
 * becoming one child {@code Reservation} when the group is created.
 *
 * @param guestId              the guest occupying this room
 * @param roomId               the assigned room
 * @param expectedGuests       expected occupancy for this room
 * @param billedToMasterFolio  when {@code true} (and the group opens a master
 *                             folio), this room's ROOM_NIGHT/CITY_TAX charges move
 *                             to the group's master invoice at check-out instead of
 *                             staying on this room's own individual invoice
 */
public record RoomingListEntryRequest(
        @NotNull(message = "Required") UUID guestId,
        @NotNull(message = "Required") UUID roomId,
        @NotNull(message = "Required") @Min(value = 1, message = "Must be at least 1") Integer expectedGuests,
        boolean billedToMasterFolio) {
}
