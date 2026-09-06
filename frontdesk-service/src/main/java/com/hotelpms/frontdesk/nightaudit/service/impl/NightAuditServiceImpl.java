package com.hotelpms.frontdesk.nightaudit.service.impl;

import com.hotelpms.internalauth.security.TenantContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.PaymentMethodTotalDto;
import com.hotelpms.frontdesk.client.dto.PaymentSummaryClientResponse;
import com.hotelpms.frontdesk.config.NightAuditJobContext;
import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;
import com.hotelpms.frontdesk.dashboard.service.DaySheetService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.dto.NightAuditRunResponse;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
import com.hotelpms.frontdesk.nightaudit.service.NightAuditService;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link NightAuditService}.
 *
 * <p>Deliberately NOT one big {@code @Transactional} method: the RUNNING
 * claim row must survive even if the audit work itself throws, so the final
 * state (COMPLETED or FAILED) can be recorded — see the class-level note on
 * {@link NightAuditRun}. Each repository save below runs in its own
 * transaction (Spring Data's {@code save}/{@code saveAndFlush} are
 * self-transactional when no transaction is already active), and this class
 * carries no {@code @Transactional} of its own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NightAuditServiceImpl implements NightAuditService {

    private static final List<ReservationStatus> NO_SHOW_CANDIDATE_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private static final String SCHEDULED_RUN_BY = "system";
    private static final String SCHEDULED_JOB_USER = "night-audit-job";
    private static final String SCHEDULED_JOB_ROLE = "ADMIN";

    private final NightAuditRunRepository nightAuditRunRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final DaySheetService daySheetService;
    private final BillingClient billingClient;
    private final ObjectMapper objectMapper;
    private final HotelSettingsRepository hotelSettingsRepository;

    /**
     * Entry point called by the Spring scheduler every night — closes
     * <em>yesterday's</em> business date (the day that just ended) for every
     * hotel. Runs outside an HTTP request, so a system {@link Authentication}
     * is set on {@link SecurityContextHolder} per hotel (mirroring what
     * {@code InternalAuthFilter} does for a real request) since {@link #run}
     * and the reservation/day-sheet services it calls resolve the hotel from
     * there, not from a parameter — same reasoning as {@code
     * GuestRetentionJobServiceImpl}'s per-hotel loop. One hotel's failure
     * (already caught and recorded as a FAILED row by {@link #run} itself)
     * never stops the loop for the rest.
     */
    @Scheduled(cron = "${frontdesk.night-audit.cron:0 30 3 * * *}")
    public void runScheduledNightAudit() {
        final LocalDate businessDate = LocalDate.now().minusDays(1);
        final List<UUID> hotelIds = hotelSettingsRepository.findAll().stream()
                .map(HotelSettings::getHotelId)
                .toList();
        log.info("[NIGHT_AUDIT] Scheduled run starting | businessDate={} | hotels={}",
                businessDate, hotelIds.size());
        for (final UUID hotelId : hotelIds) {
            final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    SCHEDULED_JOB_USER, "",
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "ROLE_" + SCHEDULED_JOB_ROLE)));
            auth.setDetails(hotelId.toString());
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                run(businessDate, SCHEDULED_RUN_BY);
            } catch (final ConflictException e) {
                // run() already catches DataAccessException itself and records it as a
                // FAILED row; only ConflictException can still escape (thrown before
                // the try block — NIGHT_AUDIT_ALREADY_CLOSED if a manual run beat the
                // scheduler to this date) so one hotel never aborts the loop for the rest.
                log.warn("[NIGHT_AUDIT] Scheduled run skipped for hotel {} | reason={}",
                        hotelId, e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
        log.info("[NIGHT_AUDIT] Scheduled run complete | businessDate={}", businessDate);
    }

    /** {@inheritDoc} */
    @Override
    public NightAuditRunResponse run(@NonNull final LocalDate businessDate, @NonNull final String runBy) {
        if (businessDate.isAfter(LocalDate.now())) {
            // Not just belt-and-suspenders: markNoShows' candidate query filters by
            // checkInDate <= businessDate, not <= today — closing a future date would
            // pull in reservations that haven't even reached their check-in day yet
            // (verifyNoShowAllowed's own real-"now" check would then reject each one
            // individually, but the day-sheet snapshot for a day that hasn't happened
            // is meaningless regardless, so this is rejected up front).
            throw new BadRequestException("NIGHT_AUDIT_BUSINESS_DATE_IN_FUTURE");
        }
        final UUID hotelId = TenantContext.resolveHotelId();
        reclaimOrRejectExisting(hotelId, businessDate);

        final NightAuditRun claim = NightAuditRun.builder()
                .hotelId(hotelId)
                .businessDate(businessDate)
                .status(NightAuditStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .runBy(runBy)
                .cashSummaryDegraded(false)
                .build();
        final NightAuditRun saved = nightAuditRunRepository.saveAndFlush(claim);
        log.info("[NIGHT_AUDIT] STARTED | hotelId={} | businessDate={} | runBy={}", hotelId, businessDate, runBy);

        try {
            final int noShowsMarked = markNoShows(hotelId, businessDate);
            final DaySheetResponse daySheet = daySheetService.getDaySheet(businessDate, hotelId);
            final PaymentSummaryClientResponse cashSummary = fetchCashSummary(hotelId, businessDate);

            saved.setStatus(NightAuditStatus.COMPLETED);
            saved.setCompletedAt(LocalDateTime.now());
            saved.setArrivals(daySheet.todayArrivals());
            saved.setDepartures(daySheet.todayDepartures());
            saved.setGuestsInHouse(daySheet.guestsInHouse());
            saved.setCurrentStays(daySheet.currentStays());
            saved.setAvailableRooms(daySheet.availableRooms());
            saved.setNoShowsMarked(noShowsMarked);
            saved.setCashSummaryDegraded(cashSummary.grandTotal() == null);
            saved.setCashSummaryJson(writeCashSummaryJson(cashSummary.byMethod()));

            final NightAuditRun completed = nightAuditRunRepository.saveAndFlush(saved);
            log.info("[NIGHT_AUDIT] COMPLETED | hotelId={} | businessDate={} | noShowsMarked={} | "
                            + "cashSummaryDegraded={}",
                    hotelId, businessDate, noShowsMarked, completed.isCashSummaryDegraded());
            return toResponse(completed);
        } catch (final DataAccessException e) {
            final String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            saved.setStatus(NightAuditStatus.FAILED);
            saved.setCompletedAt(LocalDateTime.now());
            saved.setFailureReason(reason.length() > MAX_FAILURE_REASON_LENGTH
                    ? reason.substring(0, MAX_FAILURE_REASON_LENGTH) : reason);
            final NightAuditRun failed = nightAuditRunRepository.saveAndFlush(saved);
            log.error("[NIGHT_AUDIT] FAILED | hotelId={} | businessDate={} | reason={}",
                    hotelId, businessDate, reason, e);
            return toResponse(failed);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Page<NightAuditRunResponse> getHistory(final Pageable pageable) {
        final UUID hotelId = TenantContext.resolveHotelId();
        return nightAuditRunRepository.findByHotelIdOrderByBusinessDateDesc(hotelId, pageable)
                .map(this::toResponse);
    }

    /**
     * Rejects re-running an already-closed date; hard-deletes a prior FAILED
     * row for the same date, since it recorded no real closing and a retry
     * should not collide with the unique (hotel_id, business_date) constraint.
     *
     * @param hotelId      the hotel to check
     * @param businessDate the business date to check
     */
    private void reclaimOrRejectExisting(final UUID hotelId, final LocalDate businessDate) {
        nightAuditRunRepository.findByHotelIdAndBusinessDate(hotelId, businessDate).ifPresent(existing -> {
            if (existing.getStatus() == NightAuditStatus.COMPLETED) {
                throw new ConflictException("NIGHT_AUDIT_ALREADY_CLOSED");
            }
            nightAuditRunRepository.delete(existing);
        });
    }

    /**
     * Auto-transitions eligible candidates to NO_SHOW, one at a time — a
     * candidate rejected by {@code updateStatusAndGuestsForHotel}'s own
     * guards (e.g. a Stay was created moments ago) is skipped, not fatal to
     * the rest of the batch.
     *
     * @param hotelId      the hotel to scope candidates to
     * @param businessDate the business date being audited
     * @return the number of reservations actually transitioned to NO_SHOW
     */
    private int markNoShows(final UUID hotelId, final LocalDate businessDate) {
        final List<Reservation> candidates = reservationRepository
                .findByHotelIdAndCheckInDateLessThanEqualAndStatusIn(hotelId, businessDate, NO_SHOW_CANDIDATE_STATUSES);
        int marked = 0;
        for (final Reservation candidate : candidates) {
            try {
                reservationService.updateStatusAndGuestsForHotel(
                        hotelId, candidate.getId(), ReservationStatus.NO_SHOW, null, null);
                marked++;
            } catch (final ConflictException e) {
                log.debug("[NIGHT_AUDIT] no-show skipped | hotelId={} | reservationId={} | reason={}",
                        hotelId, candidate.getId(), e.getMessage());
            }
        }
        return marked;
    }

    /**
     * Fetches the cash-closing summary from billing-service. Wrapped with
     * {@link NightAuditJobContext} unconditionally: when this call originates
     * from an HTTP request (the manual trigger), {@code
     * InternalFeignAuthInterceptor} already prefers the inbound request's own
     * headers and never consults this fallback; the ThreadLocal only matters
     * — and is required — for the scheduled path, which has no inbound
     * request to forward.
     *
     * @param hotelId      the hotel being audited
     * @param businessDate the business date to summarize
     * @return the cash summary, possibly degraded (see {@link
     *         BillingClient#getPaymentSummaryFallback})
     */
    private PaymentSummaryClientResponse fetchCashSummary(final UUID hotelId, final LocalDate businessDate) {
        NightAuditJobContext.set(hotelId.toString());
        try {
            return billingClient.getPaymentSummary(businessDate);
        } finally {
            NightAuditJobContext.clear();
        }
    }

    private String writeCashSummaryJson(final List<PaymentMethodTotalDto> byMethod) {
        try {
            return objectMapper.writeValueAsString(byMethod);
        } catch (final JsonProcessingException e) {
            log.warn("[NIGHT_AUDIT] failed to serialize cash summary — storing as degraded", e);
            return null;
        }
    }

    private List<PaymentMethodTotalDto> readCashSummaryJson(final String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(PaymentMethodTotalDto.class).readValue(json);
        } catch (final JsonProcessingException e) {
            log.warn("[NIGHT_AUDIT] failed to deserialize stored cash summary — returning empty", e);
            return List.of();
        }
    }

    private NightAuditRunResponse toResponse(final NightAuditRun run) {
        return new NightAuditRunResponse(
                run.getId(),
                run.getBusinessDate(),
                run.getStatus(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getRunBy(),
                run.getArrivals(),
                run.getDepartures(),
                run.getGuestsInHouse(),
                run.getCurrentStays(),
                run.getAvailableRooms(),
                run.getNoShowsMarked(),
                readCashSummaryJson(run.getCashSummaryJson()),
                run.isCashSummaryDegraded(),
                run.getFailureReason());
    }

}
