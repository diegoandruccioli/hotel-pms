package com.hotelpms.frontdesk.groups.dto;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One rooming-list row as returned to the client: a group member reservation,
 * enriched with the guest's display name (resolved via guest-service, same as
 * every other reservation listing).
 *
 * @param reservationId       the child reservation's id
 * @param guestId             the occupying guest's id
 * @param guestFullName       the occupying guest's display name
 * @param roomId              the assigned room's id
 * @param expectedGuests      expected occupancy for this room
 * @param actualGuests        actual occupancy once checked in
 * @param checkInDate         this room's check-in date
 * @param checkOutDate        this room's check-out date
 * @param status              this room's reservation status
 * @param billedToMasterFolio whether this room's ROOM_NIGHT/CITY_TAX charges route
 *                            to the group's master folio at check-out
 * @param price               the snapshotted total price for this room's stay
 */
public record GroupMemberResponse(
        UUID reservationId,
        UUID guestId,
        String guestFullName,
        UUID roomId,
        Integer expectedGuests,
        Integer actualGuests,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        ReservationStatus status,
        boolean billedToMasterFolio,
        BigDecimal price) {
}
