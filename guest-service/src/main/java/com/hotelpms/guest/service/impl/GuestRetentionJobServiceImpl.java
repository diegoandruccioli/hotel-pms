package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.BillingServiceClient;
import com.hotelpms.guest.client.StayServiceClient;
import com.hotelpms.guest.client.dto.GuestInvoiceClientResponse;
import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import com.hotelpms.guest.config.BatchJobContext;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.repository.GuestRepository;
import com.hotelpms.guest.repository.GuestPrivacySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
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
 * Nightly retention job that anonymises guest profiles whose legal-hold
 * obligations have expired (T-GST-05).
 *
 * <p>The job runs at 02:00 every night and:
 * <ol>
 *   <li>Pre-filters guests whose {@code gdprConsentDate} is older than the
 *       most conservative legal minimum ({@link GuestPrivacySettings#FISCAL_MIN_YEARS}
 *       years). Guests created within the last 10 years are always skipped.</li>
 *   <li>Groups candidates by hotel and loads each hotel's configured retention
 *       period.</li>
 *   <li>Calls stay-service and billing-service to verify the two independent
 *       legal holds.</li>
 *   <li>Anonymises eligible guests: PII fields are set to {@code null}, the
 *       pseudo-name {@code GDPR/ERASED} is applied, identity documents are
 *       hard-deleted, and {@code active} is set to {@code false}.</li>
 * </ol>
 *
 * <p>Inter-service Feign calls run outside an HTTP request context. Authentication
 * headers are injected via {@link com.hotelpms.guest.config.BatchJobContext}, which
 * provides a system-level identity ({@code gdpr-retention-job / ADMIN}) scoped to the
 * hotel of the guest being processed. The circuit-breaker fallback on both Feign clients
 * defaults to {@code hasStays/hasInvoices = true} so that a downstream outage never
 * causes accidental anonymisation (fail-closed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GuestRetentionJobServiceImpl {

    private static final String LOG_PREFIX = "[GDPR-RETENTION]";
    private static final String ANON_FIRST = "GDPR";
    private static final String ANON_LAST_PREFIX = "ERASED_";

    private final GuestRepository guestRepository;
    private final GuestPrivacySettingsRepository settingsRepository;
    private final StayServiceClient stayServiceClient;
    private final BillingServiceClient billingServiceClient;

    /**
     * Entry point called by the Spring scheduler every night at 02:00.
     * Uses the most conservative pre-filter (FISCAL_MIN_YEARS = 10) so that
     * only guests who are definitely beyond all legal holds enter the pipeline.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runRetentionJob() {
        log.info("{} Starting nightly retention job", LOG_PREFIX);
        final LocalDate conservativeCutoff =
                LocalDate.now().minusYears(GuestPrivacySettings.FISCAL_MIN_YEARS);

        final List<Guest> candidates =
                guestRepository.findByGdprConsentDateBefore(conservativeCutoff);
        log.info("{} {} candidate(s) found (consent before {})",
                LOG_PREFIX, candidates.size(), conservativeCutoff);

        final Map<UUID, List<Guest>> byHotel = candidates.stream()
                .collect(Collectors.groupingBy((@NonNull Guest g) -> g.getHotelId()));

        int anonymised = 0;
        for (final Map.Entry<UUID, List<Guest>> entry : byHotel.entrySet()) {
            final UUID hotelId = entry.getKey();
            final GuestPrivacySettings settings = settingsRepository
                    .findById(Objects.requireNonNull(hotelId))
                    .orElseGet(() -> GuestPrivacySettings.builder()
                            .hotelId(hotelId)
                            .guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS)
                            .build());

            for (final Guest guest : entry.getValue()) {
                if (shouldAnonymise(guest, settings)) {
                    anonymiseGuest(guest);
                    anonymised++;
                }
            }
        }
        log.info("{} Job complete — {} guest(s) anonymised", LOG_PREFIX, anonymised);
    }

    private boolean shouldAnonymise(final Guest guest, final GuestPrivacySettings settings) {
        final UUID guestId = Objects.requireNonNull(guest.getId());
        final int retentionYears = Math.max(settings.getGuestRetentionYears(),
                GuestPrivacySettings.TULPS_MIN_YEARS);

        BatchJobContext.set(Objects.requireNonNull(guest.getHotelId()).toString());
        try {
            final GuestLastStayClientResponse stayInfo =
                    stayServiceClient.getLastStayDate(guestId);
            if (stayInfo.hasStays() && stayInfo.lastStayDate() != null) {
                final LocalDate tulpsExpiry = stayInfo.lastStayDate().plusYears(retentionYears);
                if (!LocalDate.now().isAfter(tulpsExpiry)) {
                    log.debug("{} guest={} blocked by TULPS hold (expires {})",
                            LOG_PREFIX, guestId, tulpsExpiry);
                    return false;
                }
            }

            final GuestInvoiceClientResponse invoiceInfo =
                    billingServiceClient.getLastInvoiceDate(guestId);
            if (invoiceInfo.hasInvoices() && invoiceInfo.lastInvoiceDate() != null) {
                final LocalDate fiscalExpiry = invoiceInfo.lastInvoiceDate()
                        .plusYears(GuestPrivacySettings.FISCAL_MIN_YEARS);
                if (!LocalDate.now().isAfter(fiscalExpiry)) {
                    log.debug("{} guest={} blocked by FISCAL hold (expires {})",
                            LOG_PREFIX, guestId, fiscalExpiry);
                    return false;
                }
            }

            return true;
        } finally {
            BatchJobContext.clear();
        }
    }

    private void anonymiseGuest(final Guest guest) {
        final UUID guestId = Objects.requireNonNull(guest.getId());
        log.info("{} Anonymising guest={}", LOG_PREFIX, guestId);
        guest.setFirstName(ANON_FIRST);
        guest.setLastName(ANON_LAST_PREFIX + guestId.toString().substring(0, 8));
        guest.setEmail(null);
        guest.setPhone(null);
        guest.setAddress(null);
        guest.setCity(null);
        guest.setCountry(null);
        guest.setDateOfBirth(null);
        guest.getIdentityDocuments().clear();
        guest.setActive(false);
        guestRepository.save(guest);
        log.info("{} guest={} anonymised successfully", LOG_PREFIX, guestId);
    }
}
