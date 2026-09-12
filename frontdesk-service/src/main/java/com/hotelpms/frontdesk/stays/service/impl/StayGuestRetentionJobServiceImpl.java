package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.StayInvoiceDateResponse;
import com.hotelpms.frontdesk.config.BatchJobContext;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nightly retention job that anonymises {@code StayGuest} records (the
 * Alloggiati Web schedina PII) whose legal-hold obligations have expired
 * (E22 in {@code docs/ROADMAP.md}, sibling of guest-service's {@code
 * GuestRetentionJobServiceImpl} for T-GST-05 — {@code Guest}'s own nightly
 * anonymisation never touched this table).
 *
 * <p>The job runs at 02:30 every night and:
 * <ol>
 *   <li>Pre-filters guests who have actually departed ({@code departureDate}
 *       not null) before the most conservative legal minimum ({@link
 *       #FISCAL_MIN_YEARS} years, the larger of the two windows below).</li>
 *   <li>Groups candidates by hotel and, within each hotel, by stay (to avoid
 *       one billing-service call per guest when a stay has several).</li>
 *   <li>For each stay, verifies two independent legal holds: TULPS (5 years
 *       from {@code StayGuest.departureDate}, local — no cross-service call)
 *       and FISCAL (10 years from the stay's most recent relevant invoice
 *       date, via {@link BillingClient#getLastInvoiceDateForStay}).</li>
 *   <li>Anonymises every guest of a stay once both holds have expired: PII
 *       fields are overwritten (never left null where the column is {@code
 *       NOT NULL}) and {@code active} is set to {@code false}.</li>
 * </ol>
 *
 * <p>The Feign call to billing-service runs outside an HTTP request context.
 * Authentication headers are injected via {@link BatchJobContext}, scoped to
 * the hotel of the stay being processed. The circuit-breaker fallback on
 * {@link BillingClient#getLastInvoiceDateForStay} defaults to {@code
 * hasInvoices=true} so that a downstream outage never causes accidental
 * anonymisation (fail-closed) — same posture as the guest-service job.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StayGuestRetentionJobServiceImpl {

    private static final String LOG_PREFIX = "[GDPR-RETENTION]";
    private static final String JOB_USER = "gdpr-retention-job";
    private static final String ANON_FIRST = "GDPR";
    private static final String ANON_LAST_PREFIX = "ERASED_";
    private static final String ANON_PLACEHOLDER = "ERASED";
    private static final LocalDate ANON_DATE_OF_BIRTH = LocalDate.EPOCH;

    /** Minimum retention period mandated by TULPS (Alloggiati Web data) — mirrors {@code GuestPrivacySettings}. */
    private static final int TULPS_MIN_YEARS = 5;

    /** Minimum retention period mandated by Codice Civile art. 2220 (fiscal) — mirrors {@code GuestPrivacySettings}. */
    private static final int FISCAL_MIN_YEARS = 10;

    private final StayGuestRepository stayGuestRepository;
    private final BillingClient billingClient;

    /**
     * Entry point called by the Spring scheduler every night at 02:30. Uses
     * the most conservative pre-filter ({@link #FISCAL_MIN_YEARS}, the larger
     * window) so that only guests who are plausibly beyond both legal holds
     * enter the pipeline; the real per-stay check still applies both.
     */
    @Scheduled(cron = "${frontdesk.retention.cron:0 30 2 * * *}")
    @Transactional
    public void runRetentionJob() {
        log.info("{} Starting nightly StayGuest retention job", LOG_PREFIX);
        final LocalDate conservativeCutoff = LocalDate.now().minusYears(FISCAL_MIN_YEARS);

        final List<StayGuest> candidates = stayGuestRepository.findByDepartureDateBefore(conservativeCutoff);
        log.info("{} {} candidate(s) found (departed before {})",
                LOG_PREFIX, candidates.size(), conservativeCutoff);

        final Map<UUID, List<StayGuest>> byStay = candidates.stream()
                .collect(Collectors.groupingBy(g -> Objects.requireNonNull(g.getStay()).getId()));

        int anonymised = 0;
        for (final List<StayGuest> stayGuests : byStay.values()) {
            final Stay stay = Objects.requireNonNull(stayGuests.get(0).getStay());
            if (shouldAnonymiseStay(stay, stayGuests)) {
                for (final StayGuest guest : stayGuests) {
                    anonymiseGuest(guest);
                    anonymised++;
                }
            }
        }
        log.info("{} Job complete — {} guest(s) anonymised", LOG_PREFIX, anonymised);
    }

    private boolean shouldAnonymiseStay(final Stay stay, final List<StayGuest> stayGuests) {
        final UUID stayId = Objects.requireNonNull(stay.getId());
        final UUID hotelId = Objects.requireNonNull(stay.getHotelId());

        final LocalDate latestDeparture = stayGuests.stream()
                .map(StayGuest::getDepartureDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestDeparture == null) {
            return false;
        }
        final LocalDate tulpsExpiry = latestDeparture.plusYears(TULPS_MIN_YEARS);
        if (!LocalDate.now().isAfter(tulpsExpiry)) {
            log.debug("{} stay={} blocked by TULPS hold (expires {})", LOG_PREFIX, stayId, tulpsExpiry);
            return false;
        }

        BatchJobContext.set(JOB_USER, hotelId.toString());
        try {
            final StayInvoiceDateResponse invoiceInfo = billingClient.getLastInvoiceDateForStay(stayId);
            if (invoiceInfo.hasInvoices()) {
                if (invoiceInfo.lastInvoiceDate() == null) {
                    // hasInvoices()=true with no date means the source is known to have an
                    // invoice on record but the date could not be determined (e.g. the
                    // circuit-breaker fallback fired) — indeterminate must block, never be
                    // silently skipped as "no hold".
                    log.warn("{} stay={} FISCAL status indeterminate (billing-service degraded) "
                            + "— skipping this run as a precaution", LOG_PREFIX, stayId);
                    return false;
                }
                final LocalDate fiscalExpiry = invoiceInfo.lastInvoiceDate().plusYears(FISCAL_MIN_YEARS);
                if (!LocalDate.now().isAfter(fiscalExpiry)) {
                    log.debug("{} stay={} blocked by FISCAL hold (expires {})",
                            LOG_PREFIX, stayId, fiscalExpiry);
                    return false;
                }
            }
            return true;
        } finally {
            BatchJobContext.clear();
        }
    }

    private void anonymiseGuest(final StayGuest guest) {
        final UUID guestId = Objects.requireNonNull(guest.getId());
        log.info("{} Anonymising StayGuest={}", LOG_PREFIX, guestId);
        guest.setFirstName(ANON_FIRST);
        guest.setLastName(ANON_LAST_PREFIX + guestId.toString().substring(0, 8));
        // gender/placeOfBirth/citizenship/dateOfBirth are NOT NULL columns (tracciato
        // Alloggiati rules) — overwritten with fixed sentinels instead of null, same
        // non-identifying-literal approach as firstName/lastName above.
        guest.setGender(ANON_PLACEHOLDER);
        guest.setPlaceOfBirth(ANON_PLACEHOLDER);
        guest.setCitizenship(ANON_PLACEHOLDER);
        guest.setDateOfBirth(ANON_DATE_OF_BIRTH);
        guest.setDocumentType(null);
        guest.setDocumentNumber(null);
        guest.setDocumentPlaceOfIssue(null);
        guest.setTravelPurpose(null);
        guest.setActive(false);
        stayGuestRepository.save(guest);
        log.info("{} StayGuest={} anonymised successfully", LOG_PREFIX, guestId);
    }
}
