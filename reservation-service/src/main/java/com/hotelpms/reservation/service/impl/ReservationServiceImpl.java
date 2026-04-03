package com.hotelpms.reservation.service.impl;

import com.hotelpms.reservation.client.InventoryClient;
import com.hotelpms.reservation.client.dto.RoomResponse;
import com.hotelpms.reservation.domain.Reservation;
import com.hotelpms.reservation.domain.ReservationStatus;
import com.hotelpms.reservation.dto.ReservationLineItemRequest;
import com.hotelpms.reservation.dto.ReservationRequest;
import com.hotelpms.reservation.dto.ReservationResponse;
import com.hotelpms.reservation.client.GuestClient;
import com.hotelpms.reservation.client.dto.GuestResponse;
import com.hotelpms.reservation.exception.BadRequestException;
import com.hotelpms.reservation.exception.ExternalServiceException;
import com.hotelpms.reservation.exception.NotFoundException;
import com.hotelpms.reservation.mapper.ReservationMapper;
import com.hotelpms.reservation.repository.ReservationRepository;
import com.hotelpms.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of ReservationService.
 */
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final String ID_NOT_NULL_MSG = "Reservation ID cannot be null";

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final InventoryClient inventoryClient;
    private final GuestClient guestClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse createReservation(final ReservationRequest request) {
        final GuestResponse guest = verifyGuestExists(request.guestId());
        verifyRoomsAvailability(request.lineItems());
        verifyNoOverlappingReservations(null, request);

        final Reservation reservation = reservationMapper.toEntity(request);
        reservation.setActualGuests(0);
        if (reservation.getStatus() == null) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
        }

        // Ensure bidirectional relationship is set correctly
        if (reservation.getLineItems() != null) {
            reservation.getLineItems().forEach(lineItem -> lineItem.setReservation(reservation));
        }

        final Reservation savedReservation = reservationRepository.save(Objects.requireNonNull(reservation));
        return enrichWithGuestName(reservationMapper.toResponse(savedReservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final Reservation reservation = findReservationByIdOrThrow(id);
        final GuestResponse guest = guestClient.getGuestById(reservation.getGuestId());
        return enrichWithGuestName(reservationMapper.toResponse(reservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservations(final Pageable pageable) {
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final Page<Reservation> reservationPage = reservationRepository.findAll(safePageable);

        final List<UUID> guestIds = reservationPage.getContent().stream()
                .map(Reservation::getGuestId)
                .distinct()
                .toList();
        final java.util.Map<UUID, String> guestNameMap = guestClient.getGuestsBatch(guestIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        GuestResponse::id,
                        g -> g.firstName() + " " + g.lastName()
                ));

        return reservationPage.map(reservation -> {
            final ReservationResponse response = reservationMapper.toResponse(reservation);
            return enrichWithGuestName(response, guestNameMap.getOrDefault(reservation.getGuestId(), "Unknown Guest"));
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse updateReservation(final UUID id, final ReservationRequest request) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final Reservation existingReservation = findReservationByIdOrThrow(id);

        final GuestResponse guest = verifyGuestExists(request.guestId());
        verifyRoomsAvailability(request.lineItems());
        verifyNoOverlappingReservations(id, request);

        // For simplicity, we recreate line items on update
        existingReservation.getLineItems().clear();

        reservationMapper.updateEntityFromRequest(request, existingReservation);

        if (existingReservation.getLineItems() != null) {
            existingReservation.getLineItems().forEach(lineItem -> lineItem.setReservation(existingReservation));
        }

        final Reservation updatedReservation = reservationRepository.save(Objects.requireNonNull(existingReservation));
        return enrichWithGuestName(reservationMapper.toResponse(updatedReservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteReservation(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final Reservation reservation = findReservationByIdOrThrow(id);
        reservationRepository.delete(Objects.requireNonNull(reservation));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse updateStatusAndGuests(final UUID id, final ReservationStatus status,
            final Integer actualGuests) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final Reservation reservation = findReservationByIdOrThrow(id);

        if (status != null) {
            reservation.setStatus(status);
        }
        if (actualGuests != null) {
            recalculateActualGuests(reservation, actualGuests);
        }

        final Reservation saved = reservationRepository.save(Objects.requireNonNull(reservation));
        final GuestResponse guest = guestClient.getGuestById(saved.getGuestId());
        return enrichWithGuestName(reservationMapper.toResponse(saved), guest);
    }

    private Reservation findReservationByIdOrThrow(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND"));
    }

    /**
     * Updates the reservation status when at least one room is checked in.
     * This supports multi-room reservations where stays are created per room.
     *
     * @param reservation reservation entity to update
     * @param totalRooms total rooms in reservation (line items)
     * @param checkedInRooms number of rooms already checked-in
     */
    @SuppressWarnings("unused")
    private void updateStatusOnCheckIn(final Reservation reservation, final int totalRooms, final int checkedInRooms) {
        if (reservation == null) {
            return;
        }
        if (totalRooms <= 1) {
            reservation.setStatus(ReservationStatus.CHECKED_IN);
            return;
        }
        reservation.setStatus(checkedInRooms >= totalRooms
                ? ReservationStatus.CHECKED_IN
                : ReservationStatus.PARTIALLY_CHECKED_IN);
    }

    /**
     * Recalculates actualGuests value. Intended to be called by stay-service when
     * StayGuest records change.
     *
     * @param reservation reservation entity to update
     * @param actualGuests new actual guests count
     */

    private void recalculateActualGuests(final Reservation reservation, final int actualGuests) {
        if (reservation == null) {
            return;
        }
        reservation.setActualGuests(Math.max(0, actualGuests));
    }

    private GuestResponse verifyGuestExists(final UUID guestId) {
        if (guestId == null) {
            throw new IllegalArgumentException("Guest ID cannot be null");
        }
        try {
            return guestClient.getGuestById(guestId);
        } catch (final feign.FeignException.NotFound e) {
            throw new BadRequestException("GUEST_NOT_FOUND", e);
        } catch (final feign.FeignException e) {
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE", e);
        }
    }

    private ReservationResponse enrichWithGuestName(final ReservationResponse response, final GuestResponse guest) {
        if (guest == null) {
            return enrichWithGuestName(response, "Unknown Guest");
        }
        return enrichWithGuestName(response, guest.firstName() + " " + guest.lastName());
    }

    private ReservationResponse enrichWithGuestName(final ReservationResponse response, final String fullName) {
        return new ReservationResponse(
                response.id(),
                response.guestId(),
                fullName,
                response.expectedGuests(),
                response.actualGuests(),
                response.checkInDate(),
                response.checkOutDate(),
                response.status(),
                response.lineItems(),
                response.active(),
                response.createdAt(),
                response.updatedAt()
        );
    }

    /**
     * Verifies that every requested room is active and available.
     *
     * <p>
     * When the Inventory Service is unreachable (circuit open or retries
     * exhausted),
     * {@link InventoryClient#getRoomById} returns {@link Optional#empty()}. In that
     * case
     * we treat the situation as "room not found" and throw a
     * {@link NotFoundException},
     * preventing the reservation from being persisted against an unknown room
     * state.
     *
     * @param lineItems the requested reservation line items
     * @throws NotFoundException        when a room cannot be retrieved from
     *                                  Inventory Service
     * @throws ExternalServiceException when a room is known but currently
     *                                  unavailable
     */
    private void verifyRoomsAvailability(final List<ReservationLineItemRequest> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            return;
        }

        for (final ReservationLineItemRequest item : lineItems) {
            final Optional<RoomResponse> roomOptional = inventoryClient.getRoomById(item.roomId());

            final RoomResponse room = roomOptional.orElseThrow(() -> new NotFoundException(
                    "ROOM_NOT_FOUND"));

            if (!room.active() || "UNAVAILABLE".equalsIgnoreCase(room.status())) {
                throw new ExternalServiceException("ROOM_UNAVAILABLE");
            }
        }
    }

    private void verifyNoOverlappingReservations(final UUID excludeId, final ReservationRequest request) {
        if (request.lineItems() == null || request.lineItems().isEmpty()) {
            return;
        }

        final List<UUID> roomIds = request.lineItems().stream()
                .map(ReservationLineItemRequest::roomId)
                .toList();

        final List<Reservation> overlaps;
        if (excludeId == null) {
            overlaps = reservationRepository.findOverlappingReservationsForNew(
                    roomIds, request.checkInDate(), request.checkOutDate());
        } else {
            overlaps = reservationRepository.findOverlappingReservations(
                    roomIds, excludeId, request.checkInDate(), request.checkOutDate());
        }

        if (!overlaps.isEmpty()) {
            throw new BadRequestException("ROOM_UNAVAILABLE_DATES", 
                    new IllegalArgumentException("Overlapping reservation exists"));
        }
    }
}
