package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Validates guest/reservation/room state ahead of a check-in and resolves the
 * {@link CheckInContext} (checkout date + denormalized display info) that
 * {@link StayServiceImpl#checkIn} stamps onto the new {@code Stay}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StayCheckInValidator {

    private static final Set<ReservationStatus> CHECKIN_ALLOWED_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PARTIALLY_CHECKED_IN);

    private final GuestClient guestClient;
    private final ReservationService reservationService;
    private final RoomService roomService;

    /**
     * Validates the guest (via guest-service), reservation (status must be in
     * {@code CHECKIN_ALLOWED_STATUSES}), and room, then returns the reservation's
     * expected check-out date. Wraps a guest-service {@link FeignException}
     * in an {@link ExternalServiceException}; a missing/invalid room or reservation
     * propagates directly as {@link NotFoundException} / {@link IllegalStateException}.
     *
     * @param reservationId the reservation to validate
     * @param guestId       the guest to validate
     * @param roomId        the room to validate
     * @param hotelId       the authenticated hotel, for multi-tenant room scoping
     * @return check-in context with check-out date, guest display name, room number
     */
    CheckInContext validateAndGetCheckOutDate(
            final UUID reservationId, final UUID guestId, final UUID roomId, final UUID hotelId) {
        log.debug("Validating guest ID: {}", guestId);
        final GuestResponse guest;
        try {
            guest = guestClient.getGuestById(guestId);
        } catch (final FeignException ex) {
            log.warn("[STAY] CHECK_IN_FAILED | reservationId={} | reason=GUEST_SERVICE_UNAVAILABLE | detail={}",
                    reservationId, ex.getMessage());
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE: " + ex.getMessage(), ex);
        }

        log.debug("Validating reservation ID: {}", reservationId);
        final ReservationResponse reservation = reservationService.getReservationById(reservationId);
        if (!CHECKIN_ALLOWED_STATUSES.contains(reservation.status())) {
            log.warn("[STAY] CHECK_IN_FAILED | reservationId={} | reason=INVALID_RESERVATION_STATUS | currentStatus={}",
                    reservationId, reservation.status());
            throw new IllegalStateException("INVALID_RESERVATION_STATUS");
        }

        log.debug("Validating room ID: {}", roomId);
        final RoomResponse room = roomService.getRoomById(roomId, hotelId);

        final String displayName = guest.lastName() + " " + guest.firstName();
        return new CheckInContext(reservation.checkOutDate(), displayName, room.roomNumber());
    }

    /**
     * Validates a walk-in check-in by confirming guest and room exist, then returns
     * a context with the provided checkout date and denormalized display info.
     *
     * @param guestId              the guest to validate
     * @param roomId               the room to validate
     * @param expectedCheckOutDate the operator-supplied check-out date; may be null
     * @param hotelId              the authenticated hotel, for multi-tenant room scoping
     * @return context with checkout date, guest display name, room number
     */
    CheckInContext validateWalkInAndGetCheckOutDate(
            final UUID guestId, final UUID roomId, final LocalDate expectedCheckOutDate, final UUID hotelId) {
        log.debug("[STAY] WALK_IN validating guest={}", guestId);
        final GuestResponse guest;
        try {
            guest = guestClient.getGuestById(guestId);
        } catch (final FeignException ex) {
            log.warn("[STAY] WALK_IN_FAILED | reason=GUEST_SERVICE_UNAVAILABLE | detail={}", ex.getMessage());
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE: " + ex.getMessage(), ex);
        }
        log.debug("[STAY] WALK_IN validating room={}", roomId);
        final RoomResponse room = roomService.getRoomById(roomId, hotelId);
        final String displayName = guest.lastName() + " " + guest.firstName();
        return new CheckInContext(expectedCheckOutDate, displayName, room.roomNumber());
    }
}
