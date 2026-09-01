package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reconciles a reservation's status/guest-count against the stays checked in or
 * out against it. Both directions are no-ops for walk-ins, which have no
 * {@code reservationId}.
 */
@Component
@RequiredArgsConstructor
class StayReservationSync {

    private final ReservationService reservationService;
    private final StayRepository stayRepository;

    /**
     * Updates the reservation's status (PARTIALLY_CHECKED_IN / CHECKED_IN) and
     * actual guest count after a check-in against it. No-op for walk-ins.
     *
     * @param reservationId the reservation owning the just-checked-in stay; may be {@code null}
     */
    void updateReservationGuests(final UUID reservationId) {
        if (reservationId == null) {
            return;
        }
        final ReservationResponse res = reservationService.getReservationById(reservationId);
        final List<Stay> stays = stayRepository.findAllByReservationId(reservationId);

        final int actualGuests = stays.stream()
                .mapToInt(stay -> stay.getGuests() == null ? 0 : stay.getGuests().size())
                .sum();
        ReservationStatus status = null;
        if (res.lineItems() != null) {
            final int totalRooms = res.lineItems().size();
            final int checkedInRooms = stays.size();

            if (checkedInRooms >= totalRooms && totalRooms > 0) {
                status = ReservationStatus.CHECKED_IN;
            } else if (checkedInRooms > 0) {
                status = ReservationStatus.PARTIALLY_CHECKED_IN;
            }
        }

        reservationService.updateStatusAndGuests(reservationId, status, actualGuests, res.version());
    }

    /**
     * Reconciles the parent reservation's status once one of its stays checks out.
     *
     * <p>Mirrors {@link #updateReservationGuests}, but for the opposite direction of the
     * lifecycle: a reservation only moves to {@code CHECKED_OUT} once every room on it has
     * been checked out, so multi-room reservations don't flip early just because one guest
     * left. No-op for walk-ins, which have no {@code reservationId}.
     *
     * @param reservationId the reservation owning the just-checked-out stay; may be {@code null}
     */
    void updateReservationStatusAfterCheckOut(final UUID reservationId) {
        if (reservationId == null) {
            return;
        }
        final ReservationResponse reservation = reservationService.getReservationById(reservationId);
        final int totalRooms = reservation.lineItems() == null ? 0 : reservation.lineItems().size();
        final long checkedOutRooms = stayRepository.findAllByReservationId(reservationId).stream()
                .filter(s -> s.getStatus() == StayStatus.CHECKED_OUT)
                .count();

        if (totalRooms > 0 && checkedOutRooms >= totalRooms) {
            reservationService.updateStatusAndGuests(
                    reservationId, ReservationStatus.CHECKED_OUT, null, reservation.version());
        }
    }
}
