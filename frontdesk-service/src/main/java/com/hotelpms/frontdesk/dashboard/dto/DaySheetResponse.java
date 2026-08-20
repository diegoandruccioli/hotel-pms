package com.hotelpms.frontdesk.dashboard.dto;

import com.hotelpms.frontdesk.rooms.domain.RoomStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;

/**
 * Front-desk operational summary for a single date, scoped to the caller's
 * hotel. Replaces the five separate client requests (reservations, stays,
 * rooms, availability, and — for ADMIN/OWNER — the entire lifetime invoice
 * history) the Dashboard previously made to compute the same numbers
 * in the browser.
 *
 * @param date              the date this summary is for
 * @param todayArrivals     reservations checking in on {@code date} with
 *                          status {@code CONFIRMED} or {@code PENDING}
 * @param todayDepartures   reservations checking out on {@code date} with
 *                          status {@code CONFIRMED}, {@code CHECKED_IN} or
 *                          {@code PENDING}
 * @param guestsInHouse     total guests across every stay currently
 *                          {@code CHECKED_IN}
 * @param currentStays      number of stays currently {@code CHECKED_IN}
 * @param availableRooms    rooms free for a one-night stay starting
 *                          {@code date} (housekeeping-{@code CLEAN} and not
 *                          booked for that night)
 * @param roomStatusCounts  active room count per housekeeping status; a
 *                          status with no rooms in that state is simply
 *                          absent from the map rather than present with 0
 */
public record DaySheetResponse(
        LocalDate date,
        int todayArrivals,
        int todayDepartures,
        long guestsInHouse,
        long currentStays,
        int availableRooms,
        Map<RoomStatus, Long> roomStatusCounts) implements Serializable {

    /**
     * Compact constructor — defensive copy of the room-status-counts map.
     */
    public DaySheetResponse {
        roomStatusCounts = roomStatusCounts == null ? Map.of() : Map.copyOf(roomStatusCounts);
    }

    /**
     * Returns a copy of the room-status-counts map to prevent external modification.
     *
     * @return active room count per housekeeping status
     */
    @Override
    public Map<RoomStatus, Long> roomStatusCounts() {
        return Map.copyOf(roomStatusCounts);
    }
}
