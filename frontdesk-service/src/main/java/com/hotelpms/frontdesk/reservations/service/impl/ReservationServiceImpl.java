package com.hotelpms.frontdesk.reservations.service.impl;

import com.hotelpms.internalauth.security.TenantContext;

import com.hotelpms.commonweb.csv.CsvWriter;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.NotificationReservationRequest;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationGroupBillingInfo;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservedRoomCharge;
import com.hotelpms.frontdesk.reservations.mapper.ReservationMapper;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    /**
     * Legal next statuses for {@link #updateStatusAndGuests}, keyed by current status.
     * A status transitioning to itself (e.g. an {@code actualGuests}-only update that
     * resends the unchanged status) is always allowed regardless of this map — see
     * {@link #verifyValidTransition}. Terminal statuses map to an empty set.
     */
    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    ReservationStatus.PENDING, EnumSet.of(
                            ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW),
                    ReservationStatus.CONFIRMED, EnumSet.of(
                            ReservationStatus.PARTIALLY_CHECKED_IN, ReservationStatus.CHECKED_IN,
                            ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW),
                    ReservationStatus.PARTIALLY_CHECKED_IN, EnumSet.of(
                            ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT),
                    ReservationStatus.CHECKED_IN, EnumSet.of(ReservationStatus.CHECKED_OUT),
                    ReservationStatus.CHECKED_OUT, EnumSet.noneOf(ReservationStatus.class),
                    ReservationStatus.CANCELLED, EnumSet.noneOf(ReservationStatus.class),
                    ReservationStatus.NO_SHOW, EnumSet.noneOf(ReservationStatus.class));
    private static final String NOTIFICATION_SERVICE_UNAVAILABLE_REASON = "NOTIFICATION_SERVICE_UNAVAILABLE";
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";
    private static final int MAX_FAILURE_REASON_LENGTH = 500;
    private static final int GUEST_SEARCH_MATCH_CAP = 200;
    /**
     * Page size for CSV export's internal pagination loop — bounds memory to one
     * page at a time instead of loading the whole matching set before writing.
     */
    private static final int EXPORT_PAGE_SIZE = 500;
    private static final String UNKNOWN_GUEST = "Unknown Guest";
    private static final LocalDate EARLIEST_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST_FILTER_DATE = LocalDate.of(2100, 12, 31);

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final RoomService roomService;
    private final GuestClient guestClient;
    private final HotelSettingsService hotelSettingsService;
    private final NotificationClient notificationClient;
    private final RatePricingService ratePricingService;
    private final StayRepository stayRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse createReservation(final ReservationRequest request) {
        verifyDateRange(request);
        final UUID hotelId = TenantContext.resolveHotelId();
        final GuestResponse guest = verifyGuestExists(request.guestId());
        final Map<UUID, RoomResponse> roomsById = verifyRoomsAvailability(request.lineItems(), hotelId);
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
        applyResolvedPrices(reservation.getLineItems(), roomsById, hotelId, request.checkInDate(), request.checkOutDate());

        final Reservation savedReservation = saveTranslatingOverlap(Objects.requireNonNull(reservation));
        sendReservationConfirmedEmail(savedReservation, hotelId, guest, roomNumbersOf(roomsById));
        return enrichWithGuestName(reservationMapper.toResponse(savedReservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = TenantContext.resolveHotelId();
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = guestClient.getGuestById(reservation.getGuestId());
        return enrichWithGuestName(reservationMapper.toResponse(reservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservations(final Pageable pageable) {
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final UUID hotelId = TenantContext.resolveHotelId();
        final Page<Reservation> reservationPage = reservationRepository.findAllByHotelId(hotelId, safePageable);

        final List<UUID> guestIds = reservationPage.getContent().stream()
                .map((@NonNull Reservation r) -> r.getGuestId())
                .distinct()
                .toList();

        if (guestIds.isEmpty()) {
            return reservationPage.map(reservationMapper::toResponse);
        }

        final Map<UUID, String> guestNameMap = guestClient.getGuestsBatch(guestIds).stream()
                .collect(Collectors.toMap(
                        (@NonNull GuestResponse gr) -> gr.id(),
                        g -> g.firstName() + " " + g.lastName()
                ));

        return reservationPage.map(reservation -> {
            final ReservationResponse response = reservationMapper.toResponse(reservation);
            return enrichWithGuestName(response, guestNameMap.getOrDefault(reservation.getGuestId(), UNKNOWN_GUEST));
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> searchReservations(
            final String query, final boolean upcomingOnly,
            final LocalDate dateFrom, final LocalDate dateTo, final ReservationStatus status,
            final Pageable pageable) {
        final UUID hotelId = TenantContext.resolveHotelId();
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final Page<Reservation> results =
                fetchReservationsPage(hotelId, query, upcomingOnly, dateFrom, dateTo, status, safePageable);

        final List<UUID> pageGuestIds = results.getContent().stream()
                .map((@NonNull Reservation r) -> r.getGuestId())
                .distinct()
                .toList();
        if (pageGuestIds.isEmpty()) {
            return results.map(reservationMapper::toResponse);
        }

        final Map<UUID, String> guestNameMap = guestClient.getGuestsBatch(pageGuestIds).stream()
                .collect(Collectors.toMap(
                        (@NonNull GuestResponse gr) -> gr.id(),
                        g -> g.firstName() + " " + g.lastName()));

        return results.map(reservation -> enrichWithGuestName(
                reservationMapper.toResponse(reservation),
                guestNameMap.getOrDefault(reservation.getGuestId(), UNKNOWN_GUEST)));
    }

    /**
     * Shared filter-branch selection behind both {@link #searchReservations}
     * and {@link #exportReservationsCsv} — same three search strategies
     * (date/status filter, upcoming-only, plain query), picked the same way.
     *
     * @param hotelId      the caller's hotel, for tenant scoping
     * @param query        optional free-text query (guest name/email)
     * @param upcomingOnly if {@code true}, only reservations with check-in today or later
     * @param dateFrom     optional lower bound (inclusive) on check-in date
     * @param dateTo       optional upper bound (inclusive) on check-in date
     * @param status       optional reservation status filter
     * @param pageable     pagination and sorting parameters
     * @return the matching page of reservations for the selected strategy
     */
    private Page<Reservation> fetchReservationsPage(final UUID hotelId, final String query,
            final boolean upcomingOnly, final LocalDate dateFrom, final LocalDate dateTo,
            final ReservationStatus status, final Pageable pageable) {
        final String trimmedQuery = query == null || query.isBlank() ? null : query.trim();
        final List<UUID> guestIds = trimmedQuery == null ? List.of() : resolveGuestIds(trimmedQuery);

        if (dateFrom != null || dateTo != null || status != null) {
            final LocalDate effectiveFrom = dateFrom != null ? dateFrom : EARLIEST_FILTER_DATE;
            final LocalDate effectiveTo = dateTo != null ? dateTo : LATEST_FILTER_DATE;
            final Set<ReservationStatus> effectiveStatuses = status != null
                    ? Set.of(status) : EnumSet.allOf(ReservationStatus.class);
            return reservationRepository.filterReservationsByHotelId(
                    hotelId, effectiveFrom, effectiveTo, effectiveStatuses, trimmedQuery, guestIds, pageable);
        }
        if (upcomingOnly) {
            return reservationRepository.searchUpcomingReservationsByHotelId(
                    hotelId, LocalDate.now(), trimmedQuery, guestIds, pageable);
        }
        return reservationRepository.searchReservationsByHotelId(hotelId, trimmedQuery, guestIds, pageable);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public void exportReservationsCsv(final String query, final boolean upcomingOnly,
            final LocalDate dateFrom, final LocalDate dateTo, final ReservationStatus status,
            final OutputStream out) throws IOException {
        final UUID hotelId = TenantContext.resolveHotelId();
        log.info("[RESERVATION] EXPORT_CSV | hotelId={}", hotelId);

        try (CsvWriter csv = CsvWriter.open(out, List.of(
                "guestName", "checkInDate", "checkOutDate", "status", "expectedGuests", "actualGuests"))) {
            int pageNumber = 0;
            Page<Reservation> page;
            do {
                final Pageable pageable = PageRequest.of(
                        pageNumber, EXPORT_PAGE_SIZE, Sort.by("checkInDate").descending());
                page = fetchReservationsPage(hotelId, query, upcomingOnly, dateFrom, dateTo, status, pageable);

                final List<UUID> guestIds = page.getContent().stream()
                        .map((@NonNull Reservation r) -> r.getGuestId())
                        .distinct()
                        .toList();
                final Map<UUID, String> guestNameMap = guestIds.isEmpty() ? Map.of()
                        : guestClient.getGuestsBatch(guestIds).stream()
                                .collect(Collectors.toMap(
                                        (@NonNull GuestResponse gr) -> gr.id(),
                                        g -> g.firstName() + " " + g.lastName()));

                for (final Reservation reservation : page.getContent()) {
                    csv.printRow(List.of(
                            guestNameMap.getOrDefault(reservation.getGuestId(), UNKNOWN_GUEST),
                            String.valueOf(reservation.getCheckInDate()),
                            String.valueOf(reservation.getCheckOutDate()),
                            String.valueOf(reservation.getStatus()),
                            String.valueOf(reservation.getExpectedGuests()),
                            String.valueOf(reservation.getActualGuests())));
                }
                pageNumber++;
            } while (page.hasNext());
        }
    }

    /**
     * Resolves which guest IDs (within the caller's hotel) match a free-text query,
     * via a cross-service call to guest-service (Reservation only stores a guestId,
     * not a name/email). Capped at {@link #GUEST_SEARCH_MATCH_CAP} matches — reservation
     * search is a filter aid, not a guest directory export.
     *
     * @param query the free-text query (already trimmed, non-blank)
     * @return matching guest IDs, or an empty list if guest-service is unavailable
     *         (circuit breaker fallback) or nothing matched
     */
    private List<UUID> resolveGuestIds(final String query) {
        return guestClient.searchGuests(query, GUEST_SEARCH_MATCH_CAP).content().stream()
                .map((@NonNull GuestResponse gr) -> gr.id())
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse updateReservation(final UUID id, final ReservationRequest request) {
        verifyDateRange(request);
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = TenantContext.resolveHotelId();
        final Reservation existingReservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        verifyNotStale(existingReservation, request.version());
        verifyNoActiveStayConflict(id, hotelId, existingReservation, request);

        final GuestResponse guest = verifyGuestExists(request.guestId());
        final Map<UUID, RoomResponse> roomsById = verifyRoomsAvailability(request.lineItems(), hotelId);
        verifyNoOverlappingReservations(id, request);

        reservationMapper.updateEntityFromRequest(request, existingReservation);

        // Priced BEFORE the new line items join existingReservation's managed,
        // cascade-persisted collection (see ReservationMapper#updateEntityFromRequest's
        // @Mapping(target="lineItems", ignore=true) javadoc): Hibernate can snapshot a
        // newly cascade-reachable entity's INSERT state as soon as it's added to the
        // managed collection, so pricing it only after `addAll` silently never reached
        // the database (NOT NULL violation on reservation_line_items.price).
        final List<ReservationLineItem> newLineItems = request.lineItems().stream()
                .map(reservationMapper::toEntity)
                .toList();
        newLineItems.forEach(lineItem -> lineItem.setReservation(existingReservation));
        applyResolvedPrices(newLineItems, roomsById, hotelId, request.checkInDate(), request.checkOutDate());

        // For simplicity, we recreate line items on update. The removal is
        // flushed on its own, BEFORE the replacements are added: Hibernate's
        // flush ordering runs INSERTs before DELETEs within a single flush, but
        // the old line item's "delete" is actually a soft-delete UPDATE (see
        // @SQLDelete on ReservationLineItem) that excl_reservation_line_items_no_overlap
        // (V14) only excludes once `active` flips to false. Inserting the
        // replacement for the SAME room+dates before that UPDATE lands would
        // trip the constraint against the reservation's own about-to-be-removed
        // row — a false-positive ROOM_UNAVAILABLE_DATES on an edit that doesn't
        // even change the room.
        existingReservation.getLineItems().clear();
        reservationRepository.saveAndFlush(existingReservation);

        existingReservation.getLineItems().addAll(newLineItems);
        final Reservation updatedReservation = saveTranslatingOverlap(Objects.requireNonNull(existingReservation));
        return enrichWithGuestName(reservationMapper.toResponse(updatedReservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteReservation(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = TenantContext.resolveHotelId();
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        if (!DELETABLE_STATUSES.contains(reservation.getStatus())) {
            throw new ConflictException("RESERVATION_NOT_DELETABLE");
        }
        log.info("[RESERVATION] DELETED | reservationId={} | hotelId={} | statusAtDeletion={}",
                id, hotelId, reservation.getStatus());
        reservationRepository.delete(Objects.requireNonNull(reservation));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse updateStatusAndGuests(final UUID id, final ReservationStatus status,
            final Integer actualGuests, final Long clientVersion) {
        return updateStatusAndGuestsForHotel(TenantContext.resolveHotelId(), id, status, actualGuests, clientVersion);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse updateStatusAndGuestsForHotel(final UUID hotelId, final UUID id,
            final ReservationStatus status, final Integer actualGuests, final Long clientVersion) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        verifyNotStale(reservation, clientVersion);

        if (status != null && status != reservation.getStatus()) {
            verifyValidTransition(reservation.getStatus(), status);
            if (status == ReservationStatus.NO_SHOW) {
                verifyNoShowAllowed(reservation, hotelId);
            }
            log.info("[RESERVATION] STATUS_CHANGED | reservationId={} | hotelId={} | from={} | to={}",
                    id, hotelId, reservation.getStatus(), status);
            reservation.setStatus(status);
        }
        if (actualGuests != null) {
            recalculateActualGuests(reservation, actualGuests);
        }

        final Reservation saved = saveTranslatingOverlap(Objects.requireNonNull(reservation));
        final GuestResponse guest = guestClient.getGuestById(saved.getGuestId());
        return enrichWithGuestName(reservationMapper.toResponse(saved), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse retryConfirmationEmail(final UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL_MSG);
        final UUID hotelId = TenantContext.resolveHotelId();
        final Reservation reservation = findReservationByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = guestClient.getGuestById(reservation.getGuestId());
        final Map<UUID, String> roomNumbers = new java.util.HashMap<>();
        if (reservation.getLineItems() != null) {
            for (final ReservationLineItem lineItem : reservation.getLineItems()) {
                final RoomResponse room = roomService.getRoomById(lineItem.getRoomId(), hotelId);
                roomNumbers.put(lineItem.getRoomId(), room.roomNumber());
            }
        }
        sendReservationConfirmedEmail(reservation, hotelId, guest, roomNumbers);
        return enrichWithGuestName(reservationMapper.toResponse(reservation), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<ReservedRoomCharge> getReservedRoomCharge(
            final UUID reservationId, final UUID roomId, final UUID hotelId) {
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        Objects.requireNonNull(roomId, "Room ID cannot be null");
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);
        return reservationRepository.findByIdAndHotelId(reservationId, hotelId)
                .flatMap(reservation -> reservation.getLineItems().stream()
                        .filter(li -> roomId.equals(li.getRoomId()))
                        .findFirst()
                        .map(li -> new ReservedRoomCharge(li.getPrice(), reservationNights(reservation))));
    }

    private static int reservationNights(final Reservation reservation) {
        return (int) Math.max(1,
                ChronoUnit.DAYS.between(reservation.getCheckInDate(), reservation.getCheckOutDate()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveReservations(final UUID guestId) {
        Objects.requireNonNull(guestId, "Guest ID cannot be null");
        final UUID hotelId = TenantContext.resolveHotelId();
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

        final UUID hotelId = TenantContext.resolveHotelId();
        final List<RoomResponse> bookableRooms = roomService.findBookableRooms(hotelId);
        if (bookableRooms.isEmpty()) {
            return bookableRooms;
        }

        final List<UUID> roomIds = bookableRooms.stream().map((@NonNull RoomResponse rr) -> rr.id()).toList();
        final Set<UUID> bookedRoomIds = Set.copyOf(
                reservationRepository.findOverlappingRoomIds(roomIds, checkIn, checkOut));

        return bookableRooms.stream()
                .filter(room -> !bookedRoomIds.contains(room.id()))
                .map(room -> room.withResolvedTotalPrice(resolveTotalPrice(room, hotelId, checkIn, checkOut)))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean isRoomBookedByOthers(final UUID roomId, final LocalDate checkIn, final LocalDate checkOut) {
        Objects.requireNonNull(roomId, "Room ID cannot be null");
        Objects.requireNonNull(checkIn, "Check-in date cannot be null");
        Objects.requireNonNull(checkOut, "Check-out date cannot be null");
        return checkOut.isAfter(checkIn)
                && !reservationRepository.findOverlappingRoomIds(List.of(roomId), checkIn, checkOut).isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void syncLineItemRoomForCheckedInStay(
            final UUID reservationId, final UUID oldRoomId, final UUID newRoomId, final UUID hotelId) {
        Objects.requireNonNull(reservationId, ID_NOT_NULL_MSG);
        Objects.requireNonNull(oldRoomId, "Old room ID cannot be null");
        Objects.requireNonNull(newRoomId, "New room ID cannot be null");
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);

        final Reservation reservation = findReservationByIdAndHotelOrThrow(reservationId, hotelId);
        final ReservationLineItem lineItem = reservation.getLineItems().stream()
                .filter(li -> oldRoomId.equals(li.getRoomId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("RESERVATION_LINE_ITEM_NOT_FOUND"));

        final RoomResponse newRoom = roomService.getRoomById(newRoomId, hotelId);
        final List<NightlyRate> nightlyRates = ratePricingService.resolveStayRates(
                newRoom.roomType().id(), hotelId, reservation.getCheckInDate(), reservation.getCheckOutDate());
        final BigDecimal newPrice = nightlyRates.stream()
                .map(NightlyRate::nightlyPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        lineItem.setRoomId(newRoomId);
        lineItem.setPrice(newPrice);
        reservationRepository.saveAndFlush(reservation);
    }

    /**
     * Resolves the total price of {@code room}'s room type for the requested
     * stay, via {@link RatePricingService} — this is what lets the availability
     * search show a date-aware price instead of the flat {@code
     * RoomType.basePrice} the frontend used to fall back to.
     *
     * @param room    the candidate room (already known to be clean and unbooked)
     * @param hotelId the authenticated hotel, for multi-tenant pricing scope
     * @param checkIn the check-in date
     * @param checkOut the check-out date (exclusive)
     * @return the resolved total price for the stay
     */
    private BigDecimal resolveTotalPrice(
            final RoomResponse room, final UUID hotelId, final LocalDate checkIn, final LocalDate checkOut) {
        return ratePricingService.resolveStayRates(room.roomType().id(), hotelId, checkIn, checkOut).stream()
                .map(NightlyRate::nightlyPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            return enrichWithGuestName(response, UNKNOWN_GUEST);
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
                response.updatedAt(),
                response.confirmationEmailFailed(),
                response.confirmationEmailFailureReason(),
                response.version()
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
     * @return a map of roomId to the full room response for each verified room,
     *         reused both for the reservation-confirmed email (room number) and
     *         for {@link #applyResolvedPrices} (room type, to resolve the price)
     *         to avoid a second lookup
     * @throws NotFoundException        when a room does not exist for this hotel
     * @throws ExternalServiceException when a room exists but is inactive
     *                                  (soft-deleted)
     */
    private Map<UUID, RoomResponse> verifyRoomsAvailability(
            final List<ReservationLineItemRequest> lineItems, final UUID hotelId) {
        if (lineItems == null || lineItems.isEmpty()) {
            return Map.of();
        }

        final Map<UUID, RoomResponse> roomsById = new java.util.HashMap<>();
        for (final ReservationLineItemRequest item : lineItems) {
            final RoomResponse room = roomService.getRoomById(item.roomId(), hotelId);
            if (!room.active()) {
                throw new ExternalServiceException("ROOM_UNAVAILABLE");
            }
            roomsById.put(item.roomId(), room);
        }
        return roomsById;
    }

    private static Map<UUID, String> roomNumbersOf(final Map<UUID, RoomResponse> roomsById) {
        final Map<UUID, String> roomNumbers = new java.util.HashMap<>();
        roomsById.forEach((roomId, room) -> roomNumbers.put(roomId, room.roomNumber()));
        return roomNumbers;
    }

    /**
     * Resolves and snapshots the price of every line item onto {@code
     * reservation.getLineItems()}, closing the gap where a client-supplied price
     * was accepted without ever being checked against anything (T-RES / booking↔
     * invoice reconciliation). Each line item's price is the sum of
     * {@link RatePricingService#resolveStayRates} across the whole stay for that
     * room's room type — the same function {@code StayBillingCoordinator} reads
     * back (never recomputes) at check-in for reservation-based stays.
     *
     * @param lineItems the line items needing a price (already has {@code
     *                  roomId} set on each, from the mapper) — for a
     *                  not-yet-managed list this must be called BEFORE the
     *                  items are added to a persistence-context-managed,
     *                  cascade-persisted collection (see {@link
     *                  ReservationMapper#updateEntityFromRequest} javadoc)
     * @param roomsById the rooms verified by {@link #verifyRoomsAvailability},
     *                  keyed by roomId — carries the room type needed to resolve a price
     * @param hotelId   the authenticated hotel, for multi-tenant pricing scope
     * @param checkIn   the reservation's check-in date
     * @param checkOut  the reservation's check-out date (exclusive)
     */
    private void applyResolvedPrices(
            final List<ReservationLineItem> lineItems, final Map<UUID, RoomResponse> roomsById,
            final UUID hotelId, final LocalDate checkIn, final LocalDate checkOut) {
        if (lineItems == null) {
            return;
        }
        for (final ReservationLineItem lineItem : lineItems) {
            final RoomResponse room = roomsById.get(lineItem.getRoomId());
            final List<NightlyRate> nightlyRates = ratePricingService.resolveStayRates(
                    room.roomType().id(), hotelId, checkIn, checkOut);
            final BigDecimal total = nightlyRates.stream()
                    .map(NightlyRate::nightlyPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lineItem.setPrice(total);
        }
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

    /**
     * Rejects an update whose client-supplied version doesn't match the reservation's
     * current one — the "forgotten tab" scenario: a user opens the reservation, a
     * second user edits and saves it, and much later the first user saves too, silently
     * overwriting the second edit with no conflict warning. {@code @Version}/{@code
     * ObjectOptimisticLockingFailureException} alone don't catch this: the entity is
     * loaded fresh within this transaction, so Hibernate never compares it against what
     * the client actually had open — this explicit check is what closes that gap.
     *
     * <p>{@code clientVersion == null} skips the check — backward-compatible for
     * callers that don't send it yet.
     *
     * @param reservation   the reservation as currently persisted
     * @param clientVersion the version the client last read, or {@code null} to skip
     */
    private static void verifyNotStale(final Reservation reservation, final Long clientVersion) {
        if (clientVersion != null && !clientVersion.equals(reservation.getVersion())) {
            throw new ConflictException("RESERVATION_STALE_VERSION");
        }
    }

    /**
     * Rejects a status change that isn't a legal move in the reservation lifecycle
     * (see {@link #ALLOWED_TRANSITIONS}). Callers must skip this entirely when
     * {@code next} equals the current status — that's a no-op resend (e.g. an
     * {@code actualGuests}-only update), not a transition.
     *
     * @param current the status as currently persisted
     * @param next    the requested new status
     */
    private static void verifyValidTransition(final ReservationStatus current, final ReservationStatus next) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new ConflictException("RESERVATION_INVALID_TRANSITION");
        }
    }

    /**
     * A reservation can only be marked {@code NO_SHOW} once its check-in date has
     * passed and no (active) {@link com.hotelpms.frontdesk.stays.domain.Stay} was ever
     * created against it — a guest who did check in, even briefly, was not a no-show.
     *
     * @param reservation the reservation being transitioned to {@code NO_SHOW}
     * @param hotelId     the caller's hotel, for the stay lookup
     */
    private void verifyNoShowAllowed(final Reservation reservation, final UUID hotelId) {
        if (reservation.getCheckInDate().isAfter(LocalDate.now())) {
            throw new ConflictException("RESERVATION_NO_SHOW_BEFORE_CHECKIN_DATE");
        }
        final boolean hasStay = !stayRepository
                .findAllByReservationIdAndHotelId(reservation.getId(), hotelId).isEmpty();
        if (hasStay) {
            throw new ConflictException("RESERVATION_NO_SHOW_HAS_STAY");
        }
    }

    /**
     * Rejects a dates/rooms edit once check-in has already created a
     * {@code CHECKED_IN} stay from this reservation. The frontend already
     * makes the dates/rooms section read-only in that case (see
     * {@code ReservationForm.tsx}'s {@code checkedInStayId} banner) — this is
     * the server-side backstop for callers that bypass it, since a stay's own
     * dates/room/price are snapshotted at check-in and never re-read from the
     * reservation afterward: an unguarded edit here would silently desync
     * what the stay is billing from what the reservation now says.
     *
     * <p>Fields other than dates/rooms (notes, contact info, {@code
     * actualGuests}) are unaffected by this guard — only a change to what the
     * stay actually snapshotted is rejected.
     *
     * @param id                  the reservation id
     * @param hotelId             the authenticated hotel, for tenant scoping
     * @param existingReservation the reservation as currently persisted
     * @param request             the incoming update request
     * @throws ConflictException if dates or rooms changed and a
     *         {@code CHECKED_IN} stay already exists for this reservation
     */
    private void verifyNoActiveStayConflict(
            final UUID id, final UUID hotelId,
            final Reservation existingReservation, final ReservationRequest request) {
        if (!datesOrRoomsChanged(existingReservation, request)) {
            return;
        }
        if (stayRepository.existsByReservationIdAndHotelIdAndStatus(id, hotelId, StayStatus.CHECKED_IN)) {
            throw new ConflictException("RESERVATION_HAS_ACTIVE_STAY");
        }
    }

    private static boolean datesOrRoomsChanged(final Reservation existing, final ReservationRequest request) {
        if (!existing.getCheckInDate().equals(request.checkInDate())
                || !existing.getCheckOutDate().equals(request.checkOutDate())) {
            return true;
        }
        final Set<UUID> currentRoomIds = existing.getLineItems().stream()
                .map(ReservationLineItem::getRoomId)
                .collect(Collectors.toSet());
        final Set<UUID> requestedRoomIds = request.lineItems().stream()
                .map((@NonNull ReservationLineItemRequest li) -> li.roomId())
                .collect(Collectors.toSet());
        return !currentRoomIds.equals(requestedRoomIds);
    }

    private void sendReservationConfirmedEmail(
            final Reservation reservation,
            final UUID hotelId,
            final GuestResponse guest,
            final Map<UUID, String> roomNumbers) {
        try {
            final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
            if (!settings.sendReservationConfirmedEmail()) {
                return;
            }
            final int nights = (int) reservation.getCheckInDate().until(reservation.getCheckOutDate(), ChronoUnit.DAYS);
            final String roomDetails = reservation.getLineItems() != null && !reservation.getLineItems().isEmpty()
                    ? reservation.getLineItems().stream()
                            .map(li -> roomNumbers.getOrDefault(li.getRoomId(), li.getRoomId().toString()))
                            .collect(Collectors.joining(", "))
                    : "";
            final boolean sent = notificationClient.sendReservationConfirmed(new NotificationReservationRequest(
                    guest.email(),
                    guest.firstName() + " " + guest.lastName(),
                    settings.hotelName(),
                    roomDetails,
                    reservation.getCheckInDate(),
                    reservation.getCheckOutDate(),
                    nights,
                    reservation.getId().toString(),
                    "it",
                    settings.emailSubjectReservationConfirmed(),
                    settings.emailGreetingText(),
                    settings.logoUrl()));
            reservation.setConfirmationEmailFailed(!sent);
            reservation.setConfirmationEmailFailureReason(sent ? null : NOTIFICATION_SERVICE_UNAVAILABLE_REASON);
            reservationRepository.save(reservation);
        } catch (final DataAccessException | NotFoundException ex) {
            log.warn("[RESERVATION] CONFIRMED_EMAIL_SKIPPED | reservationId={} | reason={}",
                    reservation.getId(), ex.getMessage());
            reservation.setConfirmationEmailFailed(true);
            reservation.setConfirmationEmailFailureReason(truncateFailureReason(ex.getMessage()));
            reservationRepository.save(reservation);
        }
    }

    private static String truncateFailureReason(final String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_FAILURE_REASON_LENGTH
                ? message.substring(0, MAX_FAILURE_REASON_LENGTH)
                : message;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse createReservationFromPricedRooms(
            final UUID guestId, final LocalDate checkInDate, final LocalDate checkOutDate,
            final Integer expectedGuests, final Map<UUID, BigDecimal> roomPrices) {
        Objects.requireNonNull(roomPrices, "Room prices cannot be null");
        final UUID hotelId = TenantContext.resolveHotelId();
        final GuestResponse guest = verifyGuestExists(guestId);

        final List<ReservationLineItemRequest> lineItemRequests = roomPrices.keySet().stream()
                .map(ReservationLineItemRequest::new)
                .toList();
        final Map<UUID, RoomResponse> roomsById = verifyRoomsAvailability(lineItemRequests, hotelId);

        final ReservationRequest pseudoRequest = new ReservationRequest(
                guestId, Objects.requireNonNullElse(expectedGuests, 1), checkInDate, checkOutDate,
                ReservationStatus.CONFIRMED, lineItemRequests, null);
        verifyNoOverlappingReservations(null, pseudoRequest);

        final Reservation reservation = reservationMapper.toEntity(pseudoRequest);
        reservation.setHotelId(hotelId);
        reservation.setActualGuests(0);
        if (reservation.getLineItems() != null) {
            reservation.getLineItems().forEach(lineItem -> {
                lineItem.setReservation(reservation);
                lineItem.setPrice(roomPrices.get(lineItem.getRoomId()));
            });
        }

        final Reservation saved = saveTranslatingOverlap(Objects.requireNonNull(reservation));
        sendReservationConfirmedEmail(saved, hotelId, guest, roomNumbersOf(roomsById));
        return enrichWithGuestName(reservationMapper.toResponse(saved), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse createReservationForGroup(
            final UUID groupId, final UUID guestId, final UUID roomId, final int expectedGuests,
            final LocalDate checkInDate, final LocalDate checkOutDate,
            final BigDecimal groupRatePerNight, final boolean billedToMasterFolio) {
        final UUID hotelId = TenantContext.resolveHotelId();
        final GuestResponse guest = verifyGuestExists(guestId);

        final List<ReservationLineItemRequest> lineItemRequests = List.of(new ReservationLineItemRequest(roomId));
        final Map<UUID, RoomResponse> roomsById = verifyRoomsAvailability(lineItemRequests, hotelId);

        final ReservationRequest pseudoRequest = new ReservationRequest(
                guestId, expectedGuests, checkInDate, checkOutDate,
                ReservationStatus.CONFIRMED, lineItemRequests, null);
        verifyNoOverlappingReservations(null, pseudoRequest);

        final Reservation reservation = reservationMapper.toEntity(pseudoRequest);
        reservation.setHotelId(hotelId);
        reservation.setActualGuests(0);
        reservation.setGroupId(groupId);
        reservation.setBilledToMasterFolio(billedToMasterFolio);

        final BigDecimal price;
        if (groupRatePerNight != null) {
            final long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
            price = groupRatePerNight.multiply(BigDecimal.valueOf(nights));
        } else {
            final RoomResponse room = roomsById.get(roomId);
            price = ratePricingService.resolveStayRates(room.roomType().id(), hotelId, checkInDate, checkOutDate)
                    .stream().map(NightlyRate::nightlyPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        Objects.requireNonNull(reservation.getLineItems()).forEach(lineItem -> {
            lineItem.setReservation(reservation);
            lineItem.setPrice(price);
        });

        final Reservation saved = saveTranslatingOverlap(reservation);
        sendReservationConfirmedEmail(saved, hotelId, guest, roomNumbersOf(roomsById));
        return enrichWithGuestName(reservationMapper.toResponse(saved), guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<ReservationGroupBillingInfo> getGroupBillingInfo(
            final UUID reservationId, final UUID hotelId) {
        return reservationRepository.findByIdAndHotelId(reservationId, hotelId)
                .filter(r -> r.getGroupId() != null)
                .map(r -> new ReservationGroupBillingInfo(r.getGroupId(), r.isBilledToMasterFolio()));
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

    /**
     * Persists a reservation, translating a room/date exclusion-constraint
     * violation (SQLSTATE 23P01) into a clean 409 instead of a raw 500 — the
     * rare case where two requests race past the application-level overlap
     * check (Finding #5, security-report.md).
     *
     * @param reservation the reservation to persist
     * @return the persisted reservation
     */
    private Reservation saveTranslatingOverlap(final Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (final DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new ConflictException("ROOM_UNAVAILABLE_DATES", ex);
            }
            throw ex;
        }
    }

    private static boolean isExclusionViolation(final DataIntegrityViolationException ex) {
        final Throwable cause = ex.getMostSpecificCause();
        return cause instanceof final SQLException sqlException
                && SQLSTATE_EXCLUSION_VIOLATION.equals(sqlException.getSQLState());
    }
}
