package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.client.BillingClient;
import com.hotelpms.stay.client.GuestClient;
import com.hotelpms.stay.client.InventoryClient;
import com.hotelpms.stay.client.ReservationClient;
import com.hotelpms.stay.client.dto.InvoiceCreatedResponse;
import com.hotelpms.stay.client.dto.InvoiceStatusResponse;
import com.hotelpms.stay.client.dto.ReservationResponse;
import com.hotelpms.stay.client.dto.ReservationStatusUpdateRequest;
import com.hotelpms.stay.client.dto.StayInvoiceRequest;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.domain.StayStatus;
import com.hotelpms.stay.dto.GuestLastStayResponse;
import com.hotelpms.stay.dto.HotelSettingsResponse;
import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import com.hotelpms.stay.exception.BillingNotPaidException;
import com.hotelpms.stay.exception.ExternalServiceException;
import com.hotelpms.stay.exception.NotFoundException;
import com.hotelpms.stay.mapper.StayMapper;
import com.hotelpms.stay.repository.StayRepository;
import com.hotelpms.stay.service.AlloggiatiWebSenderService;
import com.hotelpms.stay.service.HotelSettingsService;
import com.hotelpms.stay.service.StayService;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StayServiceImpl implements StayService {

    private static final String PAID_STATUS = "PAID";
    private static final String ROOM_STATUS_OCCUPIED = "OCCUPIED";
    private static final Set<String> CHECKIN_ALLOWED_STATUSES = Set.of("CONFIRMED", "PARTIALLY_CHECKED_IN");

    private final StayRepository stayRepository;
    private final StayMapper stayMapper;
    private final GuestClient guestClient;
    private final ReservationClient reservationClient;
    private final InventoryClient inventoryClient;
    private final BillingClient billingClient;
    private final AlloggiatiWebSenderService alloggiatiWebSenderService;
    private final HotelSettingsService hotelSettingsService;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkIn(final StayRequest request) {
        log.info("Processing check-in | reservationId={} | walkIn={}",
                request.reservationId(), request.reservationId() == null);

        final LocalDate expectedCheckOutDate = request.reservationId() == null
                ? validateWalkInAndGetCheckOutDate(request.guestId(), request.roomId(),
                        request.expectedCheckOutDate())
                : validateAndGetCheckOutDate(request.reservationId(), request.guestId(), request.roomId());

        final Stay newStay = stayMapper.toEntity(request);
        newStay.setExpectedCheckOutDate(expectedCheckOutDate);

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
            } catch (final feign.FeignException ex) {
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

        // 2. Mark the room as DIRTY via the Inventory Service
        log.debug("Marking room {} as DIRTY after check-out", stay.getRoomId());
        inventoryClient.updateRoomStatus(stay.getRoomId(), "DIRTY");

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
        final com.hotelpms.stay.client.dto.GuestResponse guest = guestClient.getGuestById(guestId);

        // GuestClient fallback returns UNKNOWN_VALUE when guest-service is unreachable or
        // the guest profile no longer exists (anonymised). Fail-safe: return empty.
        if (GuestClient.UNKNOWN_VALUE.equals(guest.firstName())) {
            log.warn("[STAY] PRE_FILL_SKIPPED | guestId={} | reason=GUEST_PROFILE_INACTIVE_OR_UNREACHABLE", guestId);
            return Optional.empty();
        }

        log.debug("Pre-fill check: guest profile active, fetching last completed stay for guestId={}", guestId);
        return stayRepository
                .findTopByGuestIdAndStatusOrderByActualCheckInTimeDesc(guestId, StayStatus.CHECKED_OUT)
                .map(stayMapper::toDto);
    }

    /**
     * Validates the guest, reservation (status must be in {@code CHECKIN_ALLOWED_STATUSES}),
     * and room via external services, then returns the reservation's expected check-out date.
     * Wraps any {@link feign.FeignException} in an {@link ExternalServiceException}.
     *
     * @param reservationId the reservation to validate
     * @param guestId       the guest to validate
     * @param roomId        the room to validate
     * @return the reservation's check-out date (may be {@code null} if not set)
     */
    private LocalDate validateAndGetCheckOutDate(
            final UUID reservationId, final UUID guestId, final UUID roomId) {
        try {
            log.debug("Validating guest ID: {}", guestId);
            guestClient.getGuestById(guestId);

            log.debug("Validating reservation ID: {}", reservationId);
            final ReservationResponse reservation = reservationClient.getReservationById(reservationId);
            if (!CHECKIN_ALLOWED_STATUSES.contains(reservation.status())) {
                log.warn("[STAY] CHECK_IN_FAILED | reservationId={} | reason=INVALID_RESERVATION_STATUS | currentStatus={}",
                        reservationId, reservation.status());
                throw new IllegalStateException("INVALID_RESERVATION_STATUS");
            }

            log.debug("Validating room ID: {}", roomId);
            inventoryClient.getRoomById(roomId);

            return reservation.checkOutDate();
        } catch (final feign.FeignException ex) {
            log.warn("[STAY] CHECK_IN_FAILED | reservationId={} | reason=EXTERNAL_SERVICE_UNAVAILABLE | detail={}",
                    reservationId, ex.getMessage());
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validates a walk-in check-in (no reservation) by confirming guest and room exist,
     * then returns the provided expected check-out date.
     *
     * @param guestId              the guest to validate
     * @param roomId               the room to validate
     * @param expectedCheckOutDate the check-out date supplied by the operator; may be null
     * @return the expected check-out date (may be null)
     */
    private LocalDate validateWalkInAndGetCheckOutDate(
            final UUID guestId, final UUID roomId, final LocalDate expectedCheckOutDate) {
        try {
            log.debug("[STAY] WALK_IN validating guest={}", guestId);
            guestClient.getGuestById(guestId);
            log.debug("[STAY] WALK_IN validating room={}", roomId);
            inventoryClient.getRoomById(roomId);
        } catch (final feign.FeignException ex) {
            log.warn("[STAY] WALK_IN_FAILED | reason=EXTERNAL_SERVICE_UNAVAILABLE | detail={}", ex.getMessage());
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE: " + ex.getMessage(), ex);
        }
        return expectedCheckOutDate;
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
            inventoryClient.updateRoomStatus(stay.getRoomId(), ROOM_STATUS_OCCUPIED);
            log.info("[STAY] SAGA_ROOM_OCCUPIED | stayId={} | roomId={}",
                    stay.getId(), stay.getRoomId());
        } catch (final ExternalServiceException ex) {
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
        final ReservationResponse res =
                reservationClient.getReservationById(reservationId);
        final List<Stay> stays = stayRepository.findAllByReservationId(reservationId);

        final int actualGuests = stays.stream()
                .mapToInt(stay -> stay.getGuests() == null ? 0 : stay.getGuests().size())
                .sum();
        String status = null;
        if (res.lineItems() != null) {
            final int totalRooms = res.lineItems().size();
            final int checkedInRooms = stays.size();

            if (checkedInRooms >= totalRooms && totalRooms > 0) {
                status = "CHECKED_IN";
            } else if (checkedInRooms > 0) {
                status = "PARTIALLY_CHECKED_IN";
            }
        }

        reservationClient.updateStatusAndGuests(reservationId,
                new ReservationStatusUpdateRequest(status, actualGuests));
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
}
