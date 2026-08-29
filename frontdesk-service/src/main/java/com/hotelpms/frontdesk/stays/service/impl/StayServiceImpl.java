package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.GatewayEventsClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GatewayEventNotifyRequest;
import com.hotelpms.frontdesk.client.dto.GatewayEventNotifyRequest.GatewayEventType;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.exception.BillingNotPaidException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiFailureSummaryResponse;
import com.hotelpms.frontdesk.stays.dto.GuestLastStayResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.dto.StaySummaryResponse;
import com.hotelpms.frontdesk.stays.mapper.StayMapper;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.StayService;
import feign.FeignException;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the StayService interface.
 *
 * <p>Orchestrates the check-in/check-out saga; each concern that isn't core stay
 * state has its own collaborator in this package: {@link StayCheckInValidator}
 * (guest/reservation/room validation), {@link StayBillingCoordinator} (folio +
 * room charge), {@link StayAlloggiatiCoordinator} (Alloggiati Web submission),
 * {@link StayNotificationCoordinator} (checkout email), and
 * {@link StayReservationSync} (parent-reservation status reconciliation). This
 * class stays the single {@code @Service} implementing {@link StayService} — the
 * public contract doesn't change, only how the work behind it is organized.
 *
 * <p>Room and reservation lookups/updates are in-process calls to
 * {@link RoomService} / the reservation service (formerly Feign clients to
 * inventory-service / reservation-service — see ADR-001 in
 * {@code backup/DECISIONS.md}). Guest and billing remain genuinely external
 * (Feign), reached today via {@link GuestClient} directly here and via the
 * billing/notification collaborators for their respective flows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StayServiceImpl implements StayService {

    private static final String PAID_STATUS = "PAID";
    private static final String STAY_NOT_FOUND_MSG = "STAY_NOT_FOUND";
    private static final LocalDate EARLIEST_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST_FILTER_DATE = LocalDate.of(2100, 12, 31);

    private final StayRepository stayRepository;
    private final StayMapper stayMapper;
    private final GuestClient guestClient;
    private final RoomService roomService;
    private final StayCheckInValidator stayCheckInValidator;
    private final StayBillingCoordinator stayBillingCoordinator;
    private final StayAlloggiatiCoordinator stayAlloggiatiCoordinator;
    private final StayNotificationCoordinator stayNotificationCoordinator;
    private final StayReservationSync stayReservationSync;
    private final GatewayEventsClient gatewayEventsClient;
    private final CityTaxAssessmentService cityTaxAssessmentService;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkIn(final StayRequest request) {
        log.info("Processing check-in | reservationId={} | walkIn={}",
                request.reservationId(), request.reservationId() == null);

        final CheckInContext ctx = request.reservationId() == null
                ? stayCheckInValidator.validateWalkInAndGetCheckOutDate(request.guestId(), request.roomId(),
                        request.expectedCheckOutDate(), request.hotelId())
                : stayCheckInValidator.validateAndGetCheckOutDate(request.reservationId(), request.guestId(),
                        request.roomId(), request.hotelId());

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

        // Never trust a client-supplied status at creation — checkIn() always
        // produces a CHECKED_IN stay. A client sending e.g. CHECKED_OUT would
        // otherwise skip checkOut()'s BILLING_NOT_PAID guard entirely and leave
        // the room stuck OCCUPIED forever (only checkOut() ever clears it —
        // see updateHousekeepingStatus/updateRoom in RoomServiceImpl).
        newStay.setStatus(StayStatus.CHECKED_IN);

        final Stay savedStay = stayRepository.save(newStay);
        log.info("[STAY] CHECK_IN_SUCCESS | stayId={} | reservationId={} | guestId={} | roomId={}",
                savedStay.getId(), savedStay.getReservationId(),
                savedStay.getGuestId(), savedStay.getRoomId());

        // SAGA STEP 3: mark room OCCUPIED — failure triggers @Transactional rollback of the save
        markRoomOccupied(savedStay);
        gatewayEventsClient.notify(new GatewayEventNotifyRequest(GatewayEventType.CHECK_IN));

        // Non-blocking steps: execute only after OCCUPIED is confirmed
        stayBillingCoordinator.openInvoiceForStay(savedStay);
        stayAlloggiatiCoordinator.sendAlloggiatiIfEnabled(savedStay);

        // Non-blocking: only update reservation if this is a reservation-based check-in
        if (savedStay.getReservationId() != null) {
            try {
                stayReservationSync.updateReservationGuests(savedStay.getReservationId());
            } catch (final NotFoundException ex) {
                log.warn("[STAY] RESERVATION_UPDATE_FAILED | stayId={} | reason={}",
                        savedStay.getId(), ex.getMessage());
            }
        }

        return withCityTaxWarning(stayMapper.toDto(savedStay), savedStay.getId(), savedStay.getHotelId());
    }

    /**
     * Attaches the just-assessed stay's {@code cityTaxWarning}, if any, to the check-in
     * response — the "esito del check-in" surface (Parte 5.3): the operator sees the gap
     * immediately, not only later via the Dashboard summary. A read-only lookup of the
     * assessment {@code openInvoiceForStay} just wrote (never {@code assessFor} again —
     * that would risk persisting a second, wrongly-parameterized assessment if the first
     * call never actually ran, e.g. because billing-service was unreachable).
     *
     * @param response the mapped response to enrich
     * @param stayId   the just-created stay's ID
     * @param hotelId  the stay's hotel ID
     * @return the response, with {@code cityTaxWarning} set if the assessment recorded one
     */
    private StayResponse withCityTaxWarning(final StayResponse response, final UUID stayId, final UUID hotelId) {
        final CityTaxUnassessedReason warning = cityTaxAssessmentService.findAssessment(stayId, hotelId)
                .map(CityTaxAssessment::getUnassessedReason)
                .orElse(null);
        return new StayResponse(
                response.id(), response.hotelId(), response.reservationId(), response.guestId(), response.roomId(),
                response.status(), response.actualCheckInTime(), response.actualCheckOutTime(),
                response.createdAt(), response.updatedAt(), response.invoiceId(), response.alloggiatiSent(),
                response.alloggiatiSendFailed(), response.alloggiatiFailureReason(), response.guests(),
                response.guestDisplayName(), response.roomNumber(), response.expectedCheckOutDate(),
                response.invoiceCreationFailed(), response.invoiceCreationFailureReason(),
                response.checkoutEmailFailed(), response.checkoutEmailFailureReason(), warning);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkOut(@NonNull final UUID stayId, @NonNull final UUID hotelId) {
        log.info("Processing check-out for stay ID: {}", stayId);

        final Stay stay = stayRepository.findByIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException(STAY_NOT_FOUND_MSG));

        if (stay.getStatus() != StayStatus.CHECKED_IN) {
            log.warn("[STAY] CHECK_OUT_FAILED | stayId={} | reason=INVALID_STATUS | currentStatus={}",
                    stayId, stay.getStatus());
            throw new IllegalStateException("INVALID_STAY_STATUS");
        }

        // 1. Verify billing folio is PAID. Walk-in stays have no reservationId — the
        // only way to find their invoice is the invoiceId stored on the Stay itself.
        final InvoiceStatusResponse invoice = stayBillingCoordinator.resolveInvoiceForCheckOut(stay);
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

        stayReservationSync.updateReservationStatusAfterCheckOut(updatedStay.getReservationId());
        stayNotificationCoordinator.sendCheckoutEmailIfPossible(updatedStay, invoice);
        gatewayEventsClient.notify(new GatewayEventNotifyRequest(GatewayEventType.CHECK_OUT));

        return stayMapper.toDto(updatedStay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public StayResponse getStayById(@NonNull final UUID id, @NonNull final UUID hotelId) {
        log.debug("Fetching stay by ID: {}", id);
        return stayRepository.findByIdAndHotelId(id, hotelId)
                .map(stayMapper::toDto)
                .orElseThrow(() -> new NotFoundException(STAY_NOT_FOUND_MSG));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getAllStays(final Pageable pageable, @NonNull final UUID hotelId) {
        log.debug("Fetching paginated stays, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return stayRepository.findByHotelId(hotelId, pageable).map(stayMapper::toDto);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getAllStays(final Pageable pageable, @NonNull final UUID hotelId,
            final LocalDate dateFrom, final LocalDate dateTo, final StayStatus status) {
        log.debug("Fetching filtered stays, dateFrom={}, dateTo={}, status={}", dateFrom, dateTo, status);
        final LocalDateTime start = (dateFrom != null ? dateFrom : EARLIEST_FILTER_DATE).atStartOfDay();
        final LocalDateTime end = (dateTo != null ? dateTo.plusDays(1) : LATEST_FILTER_DATE).atStartOfDay();
        final Set<StayStatus> statuses = status != null ? Set.of(status) : EnumSet.allOf(StayStatus.class);
        return stayRepository.findByHotelIdAndActualCheckInTimeBetweenAndStatusIn(hotelId, start, end, statuses, pageable)
                .map(stayMapper::toDto);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getStaysByReservationId(
            @NonNull final UUID reservationId, @NonNull final UUID hotelId, final Pageable pageable) {
        log.debug("Fetching stays for reservationId: {}", reservationId);
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final List<Stay> stays = stayRepository.findAllByReservationIdAndHotelId(reservationId, hotelId);
        final List<StayResponse> content = stays.stream()
                .map(stayMapper::toDto)
                .toList();
        return pageFromList(content, safePageable);
    }

    private static <T> Page<T> pageFromList(final List<T> list, final Pageable pageable) {
        if (!pageable.isPaged()) {
            return new PageImpl<>(list, pageable, list.size());
        }
        final int start = (int) pageable.getOffset();
        if (start >= list.size()) {
            return new PageImpl<>(List.of(), pageable, list.size());
        }
        final int end = Math.min(start + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<StayResponse> getLastCompletedStayForGuest(
            @NonNull final UUID guestId, @NonNull final UUID hotelId) {
        log.debug("Pre-fill check: verifying guest profile active for guestId={}", guestId);
        final GuestResponse guest;
        try {
            guest = guestClient.getGuestById(guestId);
        } catch (final FeignException ex) {
            // Fail-safe: guest-service unreachable or guest profile not found/anonymised — skip pre-fill.
            log.warn("[STAY] PRE_FILL_SKIPPED | guestId={} | reason=GUEST_SERVICE_UNAVAILABLE_OR_NOT_FOUND | detail={}",
                    guestId, ex.getMessage());
            return Optional.empty();
        }
        log.debug("Pre-fill check: guest profile active ({} {}), fetching last completed stay for guestId={}",
                guest.firstName(), guest.lastName(), guestId);

        return stayRepository
                .findTopByGuestIdAndHotelIdAndStatusOrderByActualCheckInTimeDesc(guestId, hotelId, StayStatus.CHECKED_OUT)
                .map(stayMapper::toDto);
    }

    /**
     * Marks every stay checked in on {@code date} for {@code hotelId} as successfully sent,
     * clearing any prior failure state. Called after a successful manual
     * "Invia a Questura" submission ({@code POST /reports/alloggiati/submit}), which today
     * is the only recovery path for a failed automatic send — without this, the per-stay
     * badge would keep showing FAILED forever even after staff successfully resubmitted
     * the day's report by hand.
     *
     * @param date    the check-in date that was just (re-)submitted
     * @param hotelId the hotel UUID (tenant isolation)
     */
    @Override
    @Transactional
    public void markAlloggiatiSentForDate(@NonNull final LocalDate date, @NonNull final UUID hotelId) {
        final LocalDateTime start = date.atStartOfDay();
        final LocalDateTime end = date.plusDays(1).atStartOfDay();
        final List<Stay> stays = stayRepository.findByActualCheckInTimeBetweenAndHotelId(start, end, hotelId);
        for (final Stay stay : stays) {
            stay.setAlloggiatiSent(true);
            stay.setAlloggiatiSendFailed(false);
            stay.setAlloggiatiFailureReason(null);
        }
        stayRepository.saveAll(Objects.requireNonNull(stays));
        log.info("[STAY] ALLOGGIATI_MANUAL_SUBMIT_RECORDED | date={} | hotelId={} | staysUpdated={}",
                date, hotelId, stays.size());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse retryInvoiceCreation(@NonNull final UUID stayId, @NonNull final UUID hotelId) {
        final Stay stay = stayRepository.findByIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException(STAY_NOT_FOUND_MSG));
        stayBillingCoordinator.openInvoiceForStay(stay);
        return stayMapper.toDto(stay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse retryCheckoutEmail(@NonNull final UUID stayId, @NonNull final UUID hotelId) {
        final Stay stay = stayRepository.findByIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException(STAY_NOT_FOUND_MSG));
        if (stay.getStatus() != StayStatus.CHECKED_OUT) {
            throw new IllegalStateException("INVALID_STAY_STATUS");
        }
        final InvoiceStatusResponse invoice = stayBillingCoordinator.resolveInvoiceForCheckOut(stay);
        if (invoice == null) {
            throw new NotFoundException("INVOICE_NOT_FOUND");
        }
        stayNotificationCoordinator.sendCheckoutEmailIfPossible(stay, invoice);
        return stayMapper.toDto(stay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AlloggiatiFailureSummaryResponse getAlloggiatiFailureSummary(@NonNull final UUID hotelId) {
        final List<Stay> failed = stayRepository.findByHotelIdAndAlloggiatiSendFailedTrue(hotelId);
        final Optional<Stay> mostRecent = failed.stream()
                .max(Comparator.comparing((@NonNull Stay s) -> s.getActualCheckInTime()));
        return new AlloggiatiFailureSummaryResponse(
                failed.size(),
                mostRecent.map((@NonNull Stay s) -> s.getActualCheckInTime()).orElse(null),
                mostRecent.map((@NonNull Stay s) -> s.getAlloggiatiFailureReason()).orElse(null));
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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public GuestLastStayResponse getLastStayDateForGuest(
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
