package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;
import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillLineResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxConfigurationStatusResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxUnassessedSummaryResponse;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.CityTaxApplicability;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link CityTaxAssessmentService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CityTaxAssessmentServiceImpl implements CityTaxAssessmentService {

    private static final String ASSESSMENT_NOT_FOUND_MSG = "CITY_TAX_ASSESSMENT_NOT_FOUND";
    private static final String CITY_TAX_CHARGE_TYPE = "CITY_TAX";
    private static final String OPEN_INVOICE_STATUS = "ISSUED";
    private static final String SKIP_INVOICE_NOT_OPEN = "INVOICE_NOT_OPEN";
    private static final String SKIP_STILL_UNCONFIGURED = "STILL_UNCONFIGURED";
    private static final String SKIP_CHARGE_FAILED = "CHARGE_FAILED";
    private static final String HOTEL_ID_NOT_NULL_MSG = "Hotel ID cannot be null";

    /** The three reasons a backfill can actually fix — {@code NOT_APPLICABLE} never is. */
    private static final Set<CityTaxUnassessedReason> BACKFILLABLE_REASONS = EnumSet.of(
            CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED,
            CityTaxUnassessedReason.CATEGORY_NOT_RECORDED,
            CityTaxUnassessedReason.NO_RATE_FOR_DATE);

    /** Every reason the Dashboard summary counts — same set as {@link #BACKFILLABLE_REASONS}. */
    private static final Set<CityTaxUnassessedReason> SUMMARY_REASONS = BACKFILLABLE_REASONS;

    private final CityTaxAssessmentRepository cityTaxAssessmentRepository;
    private final CityTaxRateRepository cityTaxRateRepository;
    private final HotelCategoryHistoryRepository hotelCategoryHistoryRepository;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final StayRepository stayRepository;
    private final BillingClient billingClient;
    private final CityTaxCalculator cityTaxCalculator;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Optional<CityTaxAssessment> assessFor(final Stay stay, final long nights) {
        Objects.requireNonNull(stay, "Stay cannot be null");

        final Optional<CityTaxAssessment> existing =
                cityTaxAssessmentRepository.findByStayIdAndHotelId(stay.getId(), stay.getHotelId());
        if (existing.isPresent()) {
            return existing; // Never recomputed once assessed — fiscal audit requirement.
        }

        final LocalDate firstNight = stay.getActualCheckInTime().toLocalDate();
        final HotelSettings settings = hotelSettingsRepository.findById(stay.getHotelId()).orElse(null);
        if (settings != null && settings.getCityTaxApplicability() == CityTaxApplicability.NOT_APPLICABLE) {
            return Optional.of(persistUnassessed(stay, CityTaxUnassessedReason.NOT_APPLICABLE));
        }

        final Resolution resolution = resolve(stay.getHotelId(), firstNight);
        if (resolution.reason() != null) {
            return Optional.of(persistUnassessed(stay, resolution.reason()));
        }

        final CityTaxAssessmentResult result =
                cityTaxCalculator.assess(resolution.rate(), firstNight, nights, stay.getGuests());

        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .hotelId(stay.getHotelId())
                .stayId(stay.getId())
                .cityTaxRateId(resolution.rate().getId())
                .amountPerNightSnapshot(resolution.rate().getAmountPerNight())
                .maxTaxableNightsSnapshot(resolution.rate().getMaxTaxableNights())
                .exemptUnderAgeSnapshot(resolution.rate().getExemptUnderAge())
                .taxableGuests(result.taxableGuests())
                .taxableNights(result.taxableNights())
                .totalAmount(result.totalAmount())
                .assessedAt(LocalDateTime.now())
                .build();

        return Optional.of(cityTaxAssessmentRepository.save(assessment));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<CityTaxAssessment> findAssessment(final UUID stayId, final UUID hotelId) {
        return cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markCharged(final UUID assessmentId, final UUID billingChargeId) {
        final CityTaxAssessment assessment = cityTaxAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException(ASSESSMENT_NOT_FOUND_MSG));
        assessment.setBillingChargeId(billingChargeId);
        cityTaxAssessmentRepository.save(assessment);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public CityTaxConfigurationStatusResponse checkConfigurationStatus(final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);
        final HotelSettings settings = hotelSettingsRepository.findById(hotelId).orElse(null);
        if (settings != null && settings.getCityTaxApplicability() == CityTaxApplicability.NOT_APPLICABLE) {
            return new CityTaxConfigurationStatusResponse(true, null);
        }
        final Resolution resolution = resolve(hotelId, LocalDate.now());
        return resolution.reason() == null
                ? new CityTaxConfigurationStatusResponse(true, null)
                : new CityTaxConfigurationStatusResponse(false, resolution.reason());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public CityTaxUnassessedSummaryResponse getUnassessedSummary(final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);
        final List<CityTaxAssessment> unassessed =
                cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(hotelId, SUMMARY_REASONS);
        final Optional<CityTaxAssessment> mostRecent = unassessed.stream()
                .max(Comparator.comparing(CityTaxAssessment::getAssessedAt));
        return new CityTaxUnassessedSummaryResponse(
                unassessed.size(),
                mostRecent.map(CityTaxAssessment::getAssessedAt).orElse(null),
                mostRecent.map(CityTaxAssessment::getUnassessedReason).orElse(null));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public CityTaxBackfillResponse previewBackfill(final UUID hotelId) {
        return runBackfill(hotelId, false);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CityTaxBackfillResponse confirmBackfill(final UUID hotelId) {
        return runBackfill(hotelId, true);
    }

    /**
     * Shared preview/confirm walk over every backfillable gap for a hotel. Each stay is
     * re-resolved against <em>its own</em> check-in date using today's admin data — never
     * today's rate applied to a past stay — so a delibera that only recently caught up
     * with a comune's actual requirement still produces the historically-correct amount.
     *
     * @param hotelId the hotel UUID
     * @param apply   {@code false} for a dry-run preview (nothing written, nothing
     *                charged); {@code true} to actually post charges and correct
     *                assessment rows
     * @return the outcome
     */
    private CityTaxBackfillResponse runBackfill(final UUID hotelId, final boolean apply) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NOT_NULL_MSG);

        final HotelSettings settings = hotelSettingsRepository.findById(hotelId).orElse(null);
        if (settings != null && settings.getCityTaxApplicability() == CityTaxApplicability.NOT_APPLICABLE) {
            // The hotel has since declared the tax doesn't apply — old gaps are moot,
            // not owed, regardless of what configuration existed when they were assessed.
            return new CityTaxBackfillResponse(List.of(), BigDecimal.ZERO, 0, 0);
        }

        final List<CityTaxAssessment> gaps =
                cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(hotelId, BACKFILLABLE_REASONS);

        final List<CityTaxBackfillLineResponse> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int charged = 0;
        int skipped = 0;

        for (final CityTaxAssessment gap : gaps) {
            final Stay stay = stayRepository.findById(gap.getStayId()).orElse(null);
            if (stay == null || stay.getActualCheckInTime() == null) {
                continue; // Defensive: the stay this assessment referenced is gone or malformed.
            }
            final LocalDate checkInDate = stay.getActualCheckInTime().toLocalDate();
            final Resolution resolution = resolve(hotelId, checkInDate);
            if (resolution.reason() != null) {
                lines.add(new CityTaxBackfillLineResponse(
                        stay.getId(), checkInDate, BigDecimal.ZERO, false, SKIP_STILL_UNCONFIGURED));
                skipped++;
                continue;
            }

            final long nights = resolveNightsForBackfill(stay);
            final CityTaxAssessmentResult result =
                    cityTaxCalculator.assess(resolution.rate(), checkInDate, nights, stay.getGuests());
            total = total.add(result.totalAmount());

            if (result.totalAmount().signum() <= 0) {
                lines.add(new CityTaxBackfillLineResponse(stay.getId(), checkInDate, result.totalAmount(), false, null));
                continue;
            }
            if (!isInvoiceOpen(stay)) {
                lines.add(new CityTaxBackfillLineResponse(
                        stay.getId(), checkInDate, result.totalAmount(), false, SKIP_INVOICE_NOT_OPEN));
                skipped++;
                continue;
            }
            if (!apply) {
                lines.add(new CityTaxBackfillLineResponse(stay.getId(), checkInDate, result.totalAmount(), false, null));
                continue;
            }

            final ChargeResponse chargeResp;
            try {
                chargeResp = billingClient.addCharge(stay.getId(), buildBackfillChargeRequest(stay, resolution, result));
            } catch (final FeignException ex) {
                log.error("[CITY_TAX] BACKFILL_CHARGE_FAILED | stayId={} | reason={}", stay.getId(), ex.getMessage());
                lines.add(new CityTaxBackfillLineResponse(
                        stay.getId(), checkInDate, result.totalAmount(), false, SKIP_CHARGE_FAILED));
                skipped++;
                continue;
            }
            if (chargeResp == null || chargeResp.id() == null) {
                lines.add(new CityTaxBackfillLineResponse(
                        stay.getId(), checkInDate, result.totalAmount(), false, SKIP_CHARGE_FAILED));
                skipped++;
                continue;
            }

            gap.setCityTaxRateId(resolution.rate().getId());
            gap.setAmountPerNightSnapshot(resolution.rate().getAmountPerNight());
            gap.setMaxTaxableNightsSnapshot(resolution.rate().getMaxTaxableNights());
            gap.setExemptUnderAgeSnapshot(resolution.rate().getExemptUnderAge());
            gap.setTaxableGuests(result.taxableGuests());
            gap.setTaxableNights(result.taxableNights());
            gap.setTotalAmount(result.totalAmount());
            gap.setBillingChargeId(chargeResp.id());
            gap.setUnassessedReason(null);
            cityTaxAssessmentRepository.save(gap);
            log.info("[CITY_TAX] BACKFILL_CHARGED | stayId={} | hotelId={} | amount={}",
                    stay.getId(), hotelId, result.totalAmount());

            lines.add(new CityTaxBackfillLineResponse(stay.getId(), checkInDate, result.totalAmount(), true, null));
            charged++;
        }

        return new CityTaxBackfillResponse(lines, total, charged, skipped);
    }

    private boolean isInvoiceOpen(final Stay stay) {
        final InvoiceStatusResponse invoice = stay.getReservationId() != null
                ? billingClient.getLatestInvoiceByReservation(stay.getReservationId())
                : stay.getInvoiceId() != null ? billingClient.getInvoiceById(stay.getInvoiceId()) : null;
        return invoice != null && OPEN_INVOICE_STATUS.equalsIgnoreCase(invoice.status());
    }

    private static long resolveNightsForBackfill(final Stay stay) {
        final LocalDate checkIn = stay.getActualCheckInTime().toLocalDate();
        final LocalDate checkOut = stay.getActualCheckOutTime() != null
                ? stay.getActualCheckOutTime().toLocalDate()
                : stay.getExpectedCheckOutDate();
        if (checkOut == null || !checkOut.isAfter(checkIn)) {
            return 1;
        }
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    private static ChargeRequest buildBackfillChargeRequest(
            final Stay stay, final Resolution resolution, final CityTaxAssessmentResult result) {
        final String description = "Imposta di soggiorno (rettifica) - " + result.taxableNights()
                + " night(s) x " + result.taxableGuests() + " guest(s)";
        return new ChargeRequest(CITY_TAX_CHARGE_TYPE, description, result.totalAmount(), stay.getId(),
                resolution.rate().getAmountPerNight(), result.taxableNights());
    }

    private CityTaxAssessment persistUnassessed(final Stay stay, final CityTaxUnassessedReason reason) {
        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .hotelId(stay.getHotelId())
                .stayId(stay.getId())
                .amountPerNightSnapshot(BigDecimal.ZERO)
                .taxableGuests(0)
                .taxableNights(0)
                .totalAmount(BigDecimal.ZERO)
                .assessedAt(LocalDateTime.now())
                .unassessedReason(reason)
                .build();
        return cityTaxAssessmentRepository.save(assessment);
    }

    /**
     * Resolves the comune/category/rate chain for a hotel on a given date — the one
     * lookup shared by {@link #assessFor}, {@link #checkConfigurationStatus}, and the
     * backfill flow, so all three can never disagree on what "configured" means.
     *
     * @param hotelId the hotel UUID
     * @param date    the date to resolve against (a stay's check-in date, or today for
     *                the pre-flight check)
     * @return the resolution — {@code reason() == null} iff {@code rate()} is present
     */
    private Resolution resolve(final UUID hotelId, final LocalDate date) {
        final String comuneCodice = resolveComuneCodice(hotelId);
        if (comuneCodice == null) {
            return new Resolution(null, CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED);
        }
        final String category = hotelCategoryHistoryRepository
                .findApplicableByHotelId(hotelId, date)
                .map(HotelCategoryHistory::getCategory)
                .orElse(null);
        if (category == null) {
            return new Resolution(null, CityTaxUnassessedReason.CATEGORY_NOT_RECORDED);
        }
        final Optional<CityTaxRate> rate = cityTaxRateRepository
                .findApplicableByHotelId(hotelId, comuneCodice, category, date);
        if (rate.isEmpty()) {
            return new Resolution(null, CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        }
        return new Resolution(rate.get(), null);
    }

    private String resolveComuneCodice(final UUID hotelId) {
        return hotelSettingsRepository.findById(hotelId)
                .map(HotelSettings::getComuneCodice)
                .filter(code -> !code.isBlank())
                .orElse(null);
    }

    /**
     * The outcome of {@link #resolve}: either a usable {@code rate} ({@code reason} is
     * {@code null}), or a {@code reason} explaining why none is usable ({@code rate} is
     * {@code null}).
     *
     * @param rate   the applicable rate, or {@code null} if none was resolvable
     * @param reason why {@code rate} is {@code null}, or {@code null} if it isn't
     */
    private record Resolution(CityTaxRate rate, CityTaxUnassessedReason reason) {
    }
}
