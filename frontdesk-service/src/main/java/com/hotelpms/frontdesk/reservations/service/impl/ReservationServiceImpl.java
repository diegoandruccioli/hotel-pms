package com.hotelpms.frontdesk.reservations.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.NotificationReservationRequest;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.mapper.ReservationMapper;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of ReservationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private static final String ID_NOT_NULL_MSG = "Reservation ID cannot be null";
    private static final String HOTEL_ID_NOT_NULL_MSG = "Hotel ID cannot be null";
    private static final List<ReservationStatus> TERMINAL_STATUSES =
            List.of(ReservationStatus.CHECKED_OUT, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW);
    private static final List<ReservationStatus> DELETABLE_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final RoomService roomService;
    private final GuestClient guestClient;
    private final HotelSettingsService hotelSettingsService;
    private final NotificationClient notificationClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse createReservation(final ReservationRequest request) {
        verifyDateRange(request);
        final UUID hotelId = resolveHotelId();
        final GuestResponse guest = verifyGuestExists(request.guestId());
        final java.util.Map<UUID, String> roomNumbers = verifyRoomsAvailability(request.lineItems(), hotelId);
        verifyNoOverlappingReservations(null, request);

        final Reservation reservation = reservationMapper.toEntity(request);
        reservation.setHotelId(hotelId);
        reservation.setActualGuests(0);
        if (reservation.getStatus() == null) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
        }

        // Ensure bidirectional relationship is set correctly
        if (reservation.getLineItems() != null) {
            reservation.getLineItems().forEach(lineItem -> lineItem.setReservation(reservation));
        }

        final Reservation savedReservation = reservationRepository.save(Objects.requireNonNull(reservation));
        sendReservationConfirmedEmailIfPossible(savedReservation.getId(), hotelId, guest, request, roomNumbers);
        return enrichWithGuestName(reservationMapper.toResponse(savedReservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = resolveHotelId();
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = guestClient.getGuestById(reservation.getGuestId());
        return enrichWithGuestName(reservationMapper.toResponse(reservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservations(final Pageable pageable) {
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final UUID hotelId = resolveHotelId();
        final Page<Reservation> reservationPage = reservationRepository.findAllByHotelId(hotelId, safePageable);

        final List<UUID> guestIds = reservationPage.getContent().stream()
                .map((@NonNull Reservation r) -> r.getGuestId())
                .distinct()
                .toList();

        if (guestIds.isEmpty()) {
            return reservationPage.map(reservationMapper::toResponse);
        }

        final java.util.Map<UUID, String> guestNameMap = guestClient.getGuestsBatch(guestIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        (@NonNull GuestResponse gr) -> gr.id(),
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
        verifyDateRange(request);
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = resolveHotelId();
        final Reservation existingReservation = findReservationByIdAndHotelOrThrow(id, hotelId);

        final GuestResponse guest = verifyGuestExists(request.guestId());
        verifyRoomsAvailability(request.lineItems(), hotelId);
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
        final UUID hotelId = resolveHotelId();
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        if (!DELETABLE_STATUSES.contains(reservation.getStatus())) {
            throw new ConflictException("RESERVATION_NOT_DELETABLE");
        }
        reservationRepository.delete(Objects.requireNonNull(reservation));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse updateStatusAndGuests(final UUID id, final ReservationStatus status,
            final Integer actualGuests) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = resolveHotelId();
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);

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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveReservations(final UUID guestId) {
        Objects.requireNonNull(guestId, "Guest ID cannot be null");
        final UUID hotelId = resolveHotelId();
        return reservationRepository.existsByGuestIdAndHotelIdAndStatusNotIn(guestId, hotelId, TERMINAL_STATUSES);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getAvailableRooms(final LocalDate checkIn, final LocalDate checkOut) {
        Objects.requireNonNull(checkIn, "Check-in date cannot be null");
        Objects.requireNonNull(checkOut, "Check-out date cannot be null");
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("CHECKOUT_MUST_BE_AFTER_CHECKIN");
        }

        final UUID hotelId = resolveHotelId();
        final List<RoomResponse> cleanRooms = roomService.findCleanRooms(hotelId);
        if (cleanRooms.isEmpty()) {
            return cleanRooms;
        }

        final List<UUID> roomIds = cleanRooms.stream().map((@NonNull RoomResponse rr) -> rr.id()).toList();
        final Set<UUID> bookedRoomIds = Set.copyOf(
                reservationRepository.findOverlappingRoomIds(roomIds, checkIn, checkOut));

        return cleanRooms.stream()
                .filter(room -> !bookedRoomIds.contains(room.id()))
                .toList();
    }

    /**
     * Extracts the hotel UUID from the current security context details.
     * The value is set by {@code InternalAuthFilter} from the {@code X-Auth-Hotel} header.
     *
     * @return the hotel UUID of the authenticated user
     * @throws IllegalStateException if the hotel ID is absent or not a valid UUID
     */
    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr)) {
            throw new IllegalStateException(HOTEL_ID_NOT_NULL_MSG);
        }
        return UUID.fromString(hotelIdStr);
    }

    private Reservation findReservationByIdAndHotelOrThrow(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);
        return reservationRepository.findByIdAndHotelId(id, hotelId)
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
     * Recalculates actualGuests value. Intended to be called when StayGuest
     * records change.
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
     * Verifies that every requested room is active, scoped to the authenticated
     * hotel.
     *
     * <p>
     * Before the frontdesk-service consolidation (ADR-001) this called the
     * Inventory Service over Feign and treated a network-level failure as
     * "room not found". The room lookup is now an in-process call to
     * {@link RoomService}, so it either returns the room or throws
     * {@link NotFoundException} directly — there is no network failure mode
     * to degrade gracefully from anymore.
     *
     * @param lineItems the requested reservation line items
     * @param hotelId   the authenticated hotel, for multi-tenant room scoping
     * @return a map of roomId to roomNumber for each verified room, reused by the
     *         reservation-confirmed email to avoid a second lookup
     * @throws NotFoundException        when a room does not exist for this hotel
     * @throws ExternalServiceException when a room exists but is inactive
     *                                  (soft-deleted)
     */
    private java.util.Map<UUID, String> verifyRoomsAvailability(
            final List<ReservationLineItemRequest> lineItems, final UUID hotelId) {
        if (lineItems == null || lineItems.isEmpty()) {
            return java.util.Map.of();
        }

        final java.util.Map<UUID, String> roomNumbers = new java.util.HashMap<>();
        for (final ReservationLineItemRequest item : lineItems) {
            final RoomResponse room = roomService.getRoomById(item.roomId(), hotelId);
            if (!room.active()) {
                throw new ExternalServiceException("ROOM_UNAVAILABLE");
            }
            roomNumbers.put(item.roomId(), room.roomNumber());
        }
        return roomNumbers;
    }

    /**
     * Defense-in-depth guard: rejects requests where {@code checkOutDate} is not
     * strictly after {@code checkInDate}.
     *
     * <p>The primary enforcement is the {@code @ValidDateRange} class-level Bean
     * Validation constraint on {@link ReservationRequest}. This method adds a
     * second layer to protect programmatic callers that bypass controller validation.
     *
     * @param request the reservation request to validate
     * @throws BadRequestException if {@code checkOutDate} is equal to or before
     *         {@code checkInDate}
     */
    private static void verifyDateRange(final ReservationRequest request) {
        if (request.checkInDate() != null
                && request.checkOutDate() != null
                && !request.checkOutDate().isAfter(request.checkInDate())) {
            throw new BadRequestException("CHECKOUT_MUST_BE_AFTER_CHECKIN");
        }
    }

    private void sendReservationConfirmedEmailIfPossible(
            final UUID reservationId,
            final UUID hotelId,
            final GuestResponse guest,
            final ReservationRequest request,
            final java.util.Map<UUID, String> roomNumbers) {
        try {
            final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
            final int nights = (int) request.checkInDate().until(request.checkOutDate(), ChronoUnit.DAYS);
            final String roomDetails = request.lineItems() != null && !request.lineItems().isEmpty()
                    ? request.lineItems().stream()
                            .map(li -> roomNumbers.getOrDefault(li.roomId(), li.roomId().toString()))
                            .collect(java.util.stream.Collectors.joining(", "))
                    : "";
            notificationClient.sendReservationConfirmed(new NotificationReservationRequest(
                    guest.email(),
                    guest.firstName() + " " + guest.lastName(),
                    settings.hotelName(),
                    roomDetails,
                    request.checkInDate(),
                    request.checkOutDate(),
                    nights,
                    reservationId.toString(),
                    "it"));
        } catch (final DataAccessException | NotFoundException ex) {
            log.warn("[RESERVATION] CONFIRMED_EMAIL_SKIPPED | reservationId={} | reason={}",
                    reservationId, ex.getMessage());
        }
    }

    private void verifyNoOverlappingReservations(final UUID excludeId, final ReservationRequest request) {
        if (request.lineItems() == null || request.lineItems().isEmpty()) {
            return;
        }

        final List<UUID> roomIds = request.lineItems().stream()
                .map((@NonNull ReservationLineItemRequest li) -> li.roomId())
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
