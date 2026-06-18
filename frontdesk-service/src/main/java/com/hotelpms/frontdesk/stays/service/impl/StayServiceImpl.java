package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import com.hotelpms.frontdesk.exception.BillingNotPaidException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.GuestLastStayResponse;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.dto.StaySummaryResponse;
import com.hotelpms.frontdesk.stays.mapper.StayMapper;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import com.hotelpms.frontdesk.stays.service.StayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the StayService interface.
 *
 * <p>Room and reservation lookups/updates are in-process calls to
 * {@link RoomService} / {@link ReservationService} (formerly Feign clients to
 * inventory-service / reservation-service — see ADR-001 in
 * {@code backup/DECISIONS.md}). Guest and billing remain genuinely external
 * (Feign, via {@link GuestClient} / {@link BillingClient}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StayServiceImpl implements StayService {

    private static final String PAID_STATUS = "PAID";
    private static final Set<ReservationStatus> CHECKIN_ALLOWED_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PARTIALLY_CHECKED_IN);

    private final StayRepository stayRepository;
    private final StayMapper stayMapper;
    private final GuestClient guestClient;
    private final ReservationService reservationService;
    private final RoomService roomService;
    private final BillingClient billingClient;
    private final AlloggiatiWebSenderService alloggiatiWebSenderService;
    private final HotelSettingsService hotelSettingsService;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkIn(final StayRequest request) {
        log.info("Processing check-in | reservationId={} | walkIn={}",
                request.reservationId(), request.reservationId() == null);

        final CheckInContext ctx = request.reservationId() == null
                ? validateWalkInAndGetCheckOutDate(request.guestId(), request.roomId(),
                        request.expectedCheckOutDate(), request.hotelId())
                : validateAndGetCheckOutDate(request.reservationId(), request.guestId(), request.roomId(),
                        request.hotelId());

        final Stay newStay = stayMapper.toEntity(request);
        newStay.setExpectedCheckOutDate(ctx.checkOutDate());
        newStay.setGuestDisplayName(ctx.guestDisplayName());
        newStay.setRoomNumber(ctx.roomNumber());

        if (newStay.getGuests() != null) {
            newStay.getGuests().forEach(guest -> guest.setStay(newStay));
        }

        if (newStay.getActualCheckInTime() == null) {
            newStay.setActualCheckInTime(LocalDateTime.now());
        }

        if (newStay.getStatus() == null || newStay.getStatus() == StayStatus.EXPECTED) {
            newStay.setStatus(StayStatus.CHECKED_IN);
        }

        final Stay savedStay = stayRepository.save(newStay);
        log.info("[STAY] CHECK_IN_SUCCESS | stayId={} | reservationId={} | guestId={} | roomId={}",
                savedStay.getId(), savedStay.getReservationId(),
                savedStay.getGuestId(), savedStay.getRoomId());

        // SAGA STEP 3: mark room OCCUPIED — failure triggers @Transactional rollback of the save
        markRoomOccupied(savedStay);

        // Non-blocking steps: execute only after OCCUPIED is confirmed
        openInvoiceForStay(savedStay);
        sendAlloggiatiIfEnabled(savedStay);

        // Non-blocking: only update reservation if this is a reservation-based check-in
        if (savedStay.getReservationId() != null) {
            try {
                updateReservationGuests(savedStay.getReservationId());
            } catch (final NotFoundException ex) {
                log.warn("[STAY] RESERVATION_UPDATE_FAILED | stayId={} | reason={}",
                        savedStay.getId(), ex.getMessage());
            }
        }

        return stayMapper.toDto(savedStay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkOut(@NonNull final UUID stayId) {
        log.info("Processing check-out for stay ID: {}", stayId);

        final Stay stay = stayRepository.findById(stayId)
                .orElseThrow(() -> new NotFoundException("STAY_NOT_FOUND"));

        if (stay.getStatus() != StayStatus.CHECKED_IN) {
            log.warn("[STAY] CHECK_OUT_FAILED | stayId={} | reason=INVALID_STATUS | currentStatus={}",
                    stayId, stay.getStatus());
            throw new IllegalStateException("INVALID_STAY_STATUS");
        }

        // 1. Verify billing folio is PAID
        log.debug("Verifying billing folio for reservation: {}", stay.getReservationId());
        final InvoiceStatusResponse invoice = billingClient.getLatestInvoiceByReservation(stay.getReservationId());
        if (invoice == null || !PAID_STATUS.equalsIgnoreCase(invoice.status())) {
            log.warn("[STAY] CHECK_OUT_FAILED | stayId={} | reservationId={} | reason=BILLING_NOT_PAID",
                    stayId, stay.getReservationId());
            throw new BillingNotPaidException("BILLING_NOT_PAID");
        }

        // 2. Mark the room as DIRTY
        log.debug("Marking room {} as DIRTY after check-out", stay.getRoomId());
        roomService.updateRoomStatus(stay.getRoomId(), stay.getHotelId(), RoomStatus.DIRTY);

        // 3. Update the stay entity
        stay.setStatus(StayStatus.CHECKED_OUT);
        stay.setActualCheckOutTime(LocalDateTime.now());

        final Stay updatedStay = stayRepository.save(stay);
        log.info("[STAY] CHECK_OUT_SUCCESS | stayId={} | reservationId={} | roomId={}",
                stayId, stay.getReservationId(), stay.getRoomId());

        return stayMapper.toDto(updatedStay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public StayResponse getStayById(@NonNull final UUID id) {
        log.debug("Fetching stay by ID: {}", id);
        return stayRepository.findById(id)
                .map(stayMapper::toDto)
                .orElseThrow(() -> new NotFoundException("STAY_NOT_FOUND"));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getAllStays(final Pageable pageable) {
        log.debug("Fetching paginated stays, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return stayRepository.findAll(pageable).map(stayMapper::toDto);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getStaysByReservationId(@NonNull final UUID reservationId, final Pageable pageable) {
        log.debug("Fetching stays for reservationId: {}", reservationId);
        final List<Stay> stays = stayRepository.findAllByReservationId(reservationId);
        final List<StayResponse> content = stays.stream()
                .map(stayMapper::toDto)
                .toList();
        return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, content.size());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<StayResponse> getLastCompletedStayForGuest(@NonNull final UUID guestId) {
        log.debug("Pre-fill check: verifying guest profile active for guestId={}", guestId);
        final GuestResponse guest;
        try {
            guest = guestClient.getGuestById(guestId);
        } catch (final feign.FeignException ex) {
            // Fail-safe: guest-service unreachable or guest profile not found/anonymised — skip pre-fill.
            log.warn("[STAY] PRE_FILL_SKIPPED | guestId={} | reason=GUEST_SERVICE_UNAVAILABLE_OR_NOT_FOUND | detail={}",
                    guestId, ex.getMessage());
            return Optional.empty();
        }
        log.debug("Pre-fill check: guest profile active ({} {}), fetching last completed stay for guestId={}",
                guest.firstName(), guest.lastName(), guestId);

        return stayRepository
                .findTopByGuestIdAndStatusOrderByActualCheckInTimeDesc(guestId, StayStatus.CHECKED_OUT)
                .map(stayMapper::toDto);
    }

    /**
     * Validates the guest (via guest-service), reservation (status must be in
     * {@code CHECKIN_ALLOWED_STATUSES}), and room, then returns the reservation's
     * expected check-out date. Wraps a guest-service {@link feign.FeignException}
     * in an {@link ExternalServiceException}; a missing/invalid room or reservation
     * propagates directly as {@link NotFoundException} / {@link IllegalStateException}.
     *
     * @param reservationId the reservation to validate
     * @param guestId       the guest to validate
     * @param roomId        the room to validate
     * @param hotelId       the authenticated hotel, for multi-tenant room scoping
     * @return check-in context with check-out date, guest display name, room number
     */
    private CheckInContext validateAndGetCheckOutDate(
            final UUID reservationId, final UUID guestId, final UUID roomId, final UUID hotelId) {
        log.debug("Validating guest ID: {}", guestId);
        final GuestResponse guest;
        try {
            guest = guestClient.getGuestById(guestId);
        } catch (final feign.FeignException ex) {
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
    private CheckInContext validateWalkInAndGetCheckOutDate(
            final UUID guestId, final UUID roomId, final LocalDate expectedCheckOutDate, final UUID hotelId) {
        log.debug("[STAY] WALK_IN validating guest={}", guestId);
        final GuestResponse guest;
        try {
            guest = guestClient.getGuestById(guestId);
        } catch (final feign.FeignException ex) {
            log.warn("[STAY] WALK_IN_FAILED | reason=GUEST_SERVICE_UNAVAILABLE | detail={}", ex.getMessage());
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE: " + ex.getMessage(), ex);
        }
        log.debug("[STAY] WALK_IN validating room={}", roomId);
        final RoomResponse room = roomService.getRoomById(roomId, hotelId);
        final String displayName = guest.lastName() + " " + guest.firstName();
        return new CheckInContext(expectedCheckOutDate, displayName, room.roomNumber());
    }

    private void sendAlloggiatiIfEnabled(final Stay stay) {
        if (stay.getHotelId() == null) {
            return;
        }
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(stay.getHotelId());
        if (!settings.alloggiatiAutoSend()) {
            return;
        }
        final LocalDate checkInDate = stay.getActualCheckInTime().toLocalDate();
        try {
            alloggiatiWebSenderService.submitReport(checkInDate);
            stay.setAlloggiatiSent(true);
            stayRepository.save(stay);
            log.info("[STAY] ALLOGGIATI_SENT | stayId={} | date={}", stay.getId(), checkInDate);
        } catch (final ExternalServiceException ex) {
            log.error("[STAY] ALLOGGIATI_SEND_FAILED | stayId={} | date={} | reason={}",
                    stay.getId(), checkInDate, ex.getMessage());
        }
    }

    private void markRoomOccupied(final Stay stay) {
        try {
            roomService.updateRoomStatus(stay.getRoomId(), stay.getHotelId(), RoomStatus.OCCUPIED);
            log.info("[STAY] SAGA_ROOM_OCCUPIED | stayId={} | roomId={}",
                    stay.getId(), stay.getRoomId());
        } catch (final NotFoundException ex) {
            log.error("[STAY] SAGA_COMPENSATED | stayId={} | roomId={} | reason=ROOM_OCCUPIED_FAILED | detail={}",
                    stay.getId(), stay.getRoomId(), ex.getMessage());
            throw ex;
        }
    }

    private void openInvoiceForStay(final Stay stay) {
        final StayInvoiceRequest invoiceReq = new StayInvoiceRequest(
                stay.getId(), stay.getGuestId(), stay.getReservationId());
        final InvoiceCreatedResponse invoiceResp = billingClient.createInvoiceForStay(invoiceReq);
        if (invoiceResp != null && invoiceResp.id() != null) {
            stay.setInvoiceId(invoiceResp.id());
            stayRepository.save(stay);
            log.info("[STAY] INVOICE_CREATED | stayId={} | invoiceId={}", stay.getId(), invoiceResp.id());
        } else {
            log.error("[STAY] INVOICE_CREATION_FAILED | stayId={} | reason=BILLING_SERVICE_UNAVAILABLE",
                    stay.getId());
        }
    }

    private void updateReservationGuests(final UUID reservationId) {
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

        reservationService.updateStatusAndGuests(reservationId, status, actualGuests);
    }

    @Override
    @Transactional(readOnly = true)
    public final GuestLastStayResponse getLastStayDateForGuest(
            @NonNull final UUID guestId, @NonNull final UUID hotelId) {
        final Optional<Stay> latest = stayRepository
                .findTopByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(guestId, hotelId);
        if (latest.isEmpty() || latest.get().getActualCheckInTime() == null) {
            return new GuestLastStayResponse(false, null);
        }
        return new GuestLastStayResponse(true,
                latest.get().getActualCheckInTime().toLocalDate());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<StaySummaryResponse> getStayHistoryForGuest(
            @NonNull final UUID guestId, @NonNull final UUID hotelId) {
        return stayRepository
                .findByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(guestId, hotelId)
                .stream()
                .map(s -> new StaySummaryResponse(
                        s.getId(),
                        s.getActualCheckInTime(),
                        s.getActualCheckOutTime(),
                        s.getRoomId(),
                        s.getStatus()))
                .toList();
    }
}
