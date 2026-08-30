package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessmentLine;
import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;
import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillLineResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxConfigurationStatusResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxUnassessedSummaryResponse;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentLineRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentLineResult;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.CityTaxApplicability;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
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
    private static final String STAY_NOT_NULL_MSG = "Stay cannot be null";

    /** The three reasons a backfill can actually fix — {@code NOT_APPLICABLE} never is. */
    private static final Set<CityTaxUnassessedReason> BACKFILLABLE_REASONS = EnumSet.of(
            CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED,
            CityTaxUnassessedReason.CATEGORY_NOT_RECORDED,
            CityTaxUnassessedReason.NO_RATE_FOR_DATE);

    /** Every reason the Dashboard summary counts — same set as {@link #BACKFILLABLE_REASONS}. */
    private static final Set<CityTaxUnassessedReason> SUMMARY_REASONS = BACKFILLABLE_REASONS;

    private final CityTaxAssessmentRepository cityTaxAssessmentRepository;
    private final CityTaxAssessmentLineRepository cityTaxAssessmentLineRepository;
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
        Objects.requireNonNull(stay, STAY_NOT_NULL_MSG);

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

        final RangeResolution resolution = resolveRange(stay.getHotelId(), firstNight, nights);
        if (resolution.reason() != null) {
            return Optional.of(persistUnassessed(stay, resolution.reason()));
        }

        final CityTaxAssessmentResult result =
                cityTaxCalculator.assess(resolution.rates(), firstNight, nights, stay.getGuests());
        final CityTaxAssessmentLineResult firstLine = result.lines().get(0);

        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .hotelId(stay.getHotelId())
                .stayId(stay.getId())
                .cityTaxRateId(firstLine.cityTaxRateId())
                .amountPerNightSnapshot(firstLine.amountPerNight())
                .maxTaxableNightsSnapshot(rateById(resolution.rates(), firstLine.cityTaxRateId()).getMaxTaxableNights())
                .exemptUnderAgeSnapshot(rateById(resolution.rates(), firstLine.cityTaxRateId()).getExemptUnderAge())
                .taxableGuests(result.taxableGuests())
                .taxableNights(result.taxableNights())
                .totalAmount(result.totalAmount())
                .assessedAt(LocalDateTime.now())
                .build();

        final CityTaxAssessment saved = cityTaxAssessmentRepository.save(assessment);
        saveLines(saved.getId(), result.lines());
        return Optional.of(saved);
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
     * re-resolved against <em>its own</em> nights using today's admin data — never
     * today's rate applied to a past stay — so a delibera that only recently caught up
     * with a comune's actual requirement still produces the historically-correct amount,
     * including a per-night breakdown if the stay itself crossed a rate boundary.
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
            final long nights = resolveNightsForBackfill(stay);
            final RangeResolution resolution = resolveRange(hotelId, checkInDate, nights);
            if (resolution.reason() != null) {
                lines.add(new CityTaxBackfillLineResponse(
                        stay.getId(), checkInDate, BigDecimal.ZERO, false, SKIP_STILL_UNCONFIGURED));
                skipped++;
                continue;
            }

            final CityTaxAssessmentResult result =
                    cityTaxCalculator.assess(resolution.rates(), checkInDate, nights, stay.getGuests());
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
                chargeResp = billingClient.addCharge(stay.getId(), buildBackfillChargeRequest(stay, result));
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

            final CityTaxAssessmentLineResult firstLine = result.lines().get(0);
            gap.setCityTaxRateId(firstLine.cityTaxRateId());
            gap.setAmountPerNightSnapshot(firstLine.amountPerNight());
            gap.setMaxTaxableNightsSnapshot(rateById(resolution.rates(), firstLine.cityTaxRateId()).getMaxTaxableNights());
            gap.setExemptUnderAgeSnapshot(rateById(resolution.rates(), firstLine.cityTaxRateId()).getExemptUnderAge());
            gap.setTaxableGuests(result.taxableGuests());
            gap.setTaxableNights(result.taxableNights());
            gap.setTotalAmount(result.totalAmount());
            gap.setBillingChargeId(chargeResp.id());
            gap.setUnassessedReason(null);
            cityTaxAssessmentRepository.save(gap);
            saveLines(gap.getId(), result.lines());
            log.info("[CITY_TAX] BACKFILL_CHARGED | stayId={} | hotelId={} | amount={}",
                    stay.getId(), hotelId, result.totalAmount());

            lines.add(new CityTaxBackfillLineResponse(stay.getId(), checkInDate, result.totalAmount(), true, null));
            charged++;
        }

        return new CityTaxBackfillResponse(lines, total, charged, skipped);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void rectifyForGuestAdded(final Stay stay, final StayGuest newGuest) {
        Objects.requireNonNull(stay, STAY_NOT_NULL_MSG);
        Objects.requireNonNull(newGuest, "Guest cannot be null");

        final LocalDate arrivalDate = newGuest.getArrivalDate();
        final LocalDate stayEnd = stay.getExpectedCheckOutDate() != null
                ? stay.getExpectedCheckOutDate() : arrivalDate.plusDays(1);
        rectify(stay, arrivalDate, stayEnd, List.of(newGuest), "ospite aggiunto");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void rectifyForStayExtended(final Stay stay, final LocalDate fromDate, final LocalDate toDateExclusive) {
        Objects.requireNonNull(stay, STAY_NOT_NULL_MSG);
        Objects.requireNonNull(fromDate, "From date cannot be null");
        Objects.requireNonNull(toDateExclusive, "To date cannot be null");
        rectify(stay, fromDate, toDateExclusive, stay.getGuests(), "proroga soggiorno");
    }

    /**
     * Shared rectification logic for both a guest added mid-stay and a stay extended past
     * its original check-out: appends {@code city_tax_assessment_lines} for {@code [from,
     * toExclusive)} and adds their sum to the existing assessment's total — never rewriting
     * any other snapshot field, and never recomputing the original assessment.
     *
     * @param stay          the stay being rectified
     * @param from          the rectified range start (inclusive)
     * @param toExclusive   the rectified range end (exclusive)
     * @param guests        the guests to assess for this range (the new guest alone, or
     *                      every guest of the stay for an extension)
     * @param descriptionTag Italian label distinguishing the charge's cause on the invoice
     */
    private void rectify(final Stay stay, final LocalDate from, final LocalDate toExclusive,
            final List<StayGuest> guests, final String descriptionTag) {
        final Optional<CityTaxAssessment> existing =
                cityTaxAssessmentRepository.findByStayIdAndHotelId(stay.getId(), stay.getHotelId());
        if (existing.isEmpty() || existing.get().getUnassessedReason() != null) {
            // Nothing assessed yet to add a rectification onto — a later backfill run
            // (or the stay's own first assessFor) covers this as part of the whole stay.
            return;
        }
        if (!toExclusive.isAfter(from)) {
            return;
        }
        final CityTaxAssessment assessment = existing.get();
        final long nights = ChronoUnit.DAYS.between(from, toExclusive);

        final RangeResolution resolution = resolveRange(stay.getHotelId(), from, nights);
        if (resolution.reason() != null) {
            log.warn("[CITY_TAX] RECTIFICATION_SKIPPED | stayId={} | reason={}", stay.getId(), resolution.reason());
            return;
        }

        final CityTaxAssessmentResult result = cityTaxCalculator.assess(resolution.rates(), from, nights, guests);
        if (result.totalAmount().signum() <= 0) {
            return;
        }

        saveLines(assessment.getId(), result.lines());
        // Only the aggregate total is corrected — every other snapshot field keeps
        // reflecting the stay as first assessed, per the established convention that
        // per-segment truth lives in the lines, never in the top-level snapshot.
        assessment.setTotalAmount(assessment.getTotalAmount().add(result.totalAmount()));
        cityTaxAssessmentRepository.save(assessment);
        log.info("[CITY_TAX] RECTIFICATION_ADDED | stayId={} | reason={} | amount={}",
                stay.getId(), descriptionTag, result.totalAmount());

        if (!isInvoiceOpen(stay)) {
            log.warn("[CITY_TAX] RECTIFICATION_NOT_CHARGED | stayId={} | reason=INVOICE_NOT_OPEN", stay.getId());
            return;
        }
        try {
            billingClient.addCharge(stay.getId(), buildRectificationChargeRequest(stay, result, descriptionTag));
        } catch (final FeignException ex) {
            log.error("[CITY_TAX] RECTIFICATION_CHARGE_FAILED | stayId={} | reason={}", stay.getId(), ex.getMessage());
        }
    }

    private static ChargeRequest buildRectificationChargeRequest(
            final Stay stay, final CityTaxAssessmentResult result, final String descriptionTag) {
        final String description = "Imposta di soggiorno (rettifica - " + descriptionTag + ") - "
                + result.taxableNights() + " night(s)";
        final BigDecimal unitPrice = result.lines().size() == 1 ? result.lines().get(0).amountPerNight() : null;
        return new ChargeRequest(CITY_TAX_CHARGE_TYPE, description, result.totalAmount(), stay.getId(),
                unitPrice, result.taxableNights());
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

    /**
     * Builds the backfill charge request. {@code unitPrice} is display/audit metadata
     * only (never used to derive {@code amount}) — left {@code null} when the stay's
     * backfilled nights span more than one rate, since no single per-night figure would
     * be accurate.
     *
     * @param stay   the stay being backfilled
     * @param result the computed assessment, possibly spanning multiple rates
     * @return the charge request to post to billing-service
     */
    private static ChargeRequest buildBackfillChargeRequest(final Stay stay, final CityTaxAssessmentResult result) {
        final String description = "Imposta di soggiorno (rettifica) - " + result.taxableNights()
                + " night(s) x " + result.taxableGuests() + " guest(s)";
        final BigDecimal unitPrice = result.lines().size() == 1 ? result.lines().get(0).amountPerNight() : null;
        return new ChargeRequest(CITY_TAX_CHARGE_TYPE, description, result.totalAmount(), stay.getId(),
                unitPrice, result.taxableNights());
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

    private void saveLines(final UUID assessmentId, final List<CityTaxAssessmentLineResult> lineResults) {
        final List<CityTaxAssessmentLine> entities = lineResults.stream()
                .map(l -> CityTaxAssessmentLine.builder()
                        .assessmentId(assessmentId)
                        .fromDate(l.fromDate())
                        .toDate(l.toDateExclusive())
                        .cityTaxRateId(l.cityTaxRateId())
                        .amountPerNight(l.amountPerNight())
                        .taxableGuests(l.taxableGuests())
                        .taxableNights(l.taxableNights())
                        .subtotal(l.subtotal())
                        .build())
                .toList();
        cityTaxAssessmentLineRepository.saveAll(entities);
    }

    private static CityTaxRate rateById(final List<CityTaxRate> rates, final UUID rateId) {
        return rates.stream()
                .filter(r -> r.getId().equals(rateId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rate " + rateId + " missing from its own resolved range"));
    }

    /**
     * Resolves the comune/category/rate chain for a hotel on a single date — used by
     * {@link #checkConfigurationStatus}, which only ever asks about today. Stay
     * assessment and backfill use {@link #resolveRange} instead, since a stay's nights
     * can span more than one rate.
     *
     * @param hotelId the hotel UUID
     * @param date    the date to resolve against
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

    /**
     * Resolves every rate covering a stay's nights — {@code category} is still resolved
     * once, at {@code firstNight} (a hotel's classification doesn't change mid-stay in
     * this model), but the rate itself may not be uniform across the range, so this
     * fetches every rate overlapping it and verifies there's no gap.
     *
     * @param hotelId    the hotel UUID
     * @param firstNight the stay's first night
     * @param nights     the number of nights to cover
     * @return the resolution — {@code reason() == null} iff {@code rates()} fully
     *         covers {@code [firstNight, firstNight + nights)} with no gaps
     */
    private RangeResolution resolveRange(final UUID hotelId, final LocalDate firstNight, final long nights) {
        final String comuneCodice = resolveComuneCodice(hotelId);
        if (comuneCodice == null) {
            return new RangeResolution(null, CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED);
        }
        final String category = hotelCategoryHistoryRepository
                .findApplicableByHotelId(hotelId, firstNight)
                .map(HotelCategoryHistory::getCategory)
                .orElse(null);
        if (category == null) {
            return new RangeResolution(null, CityTaxUnassessedReason.CATEGORY_NOT_RECORDED);
        }
        final LocalDate stayEnd = firstNight.plusDays(nights);
        final List<CityTaxRate> rates = cityTaxRateRepository
                .findAllApplicableByHotelIdInRange(hotelId, comuneCodice, category, firstNight, stayEnd);
        if (rates.isEmpty() || !fullyCovers(rates, firstNight, stayEnd)) {
            return new RangeResolution(null, CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        }
        return new RangeResolution(rates, null);
    }

    /**
     * Verifies {@code rates} leaves no gap across {@code [from, toExclusive)} — a stay
     * whose delibera only covers part of its nights is treated as fully unconfigured
     * (the existing "not configured yet" contract) rather than partially assessed,
     * which would be far harder to reason about and audit.
     *
     * @param rates        candidate rates, any order
     * @param from         range start (inclusive)
     * @param toExclusive  range end (exclusive)
     * @return {@code true} if every day in the range is covered by exactly one rate
     */
    private static boolean fullyCovers(final List<CityTaxRate> rates, final LocalDate from, final LocalDate toExclusive) {
        LocalDate cursor = from;
        while (cursor.isBefore(toExclusive)) {
            final LocalDate current = cursor;
            final boolean covered = rates.stream().anyMatch(r -> !r.getValidFrom().isAfter(current)
                    && (r.getValidTo() == null || r.getValidTo().isAfter(current)));
            if (!covered) {
                return false;
            }
            cursor = cursor.plusDays(1);
        }
        return true;
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

    /**
     * The outcome of {@link #resolveRange}: either the rates covering the whole range
     * ({@code reason} is {@code null}), or a {@code reason} explaining why coverage is
     * incomplete ({@code rates} is {@code null}).
     *
     * @param rates  every rate covering the range, or {@code null} if coverage is incomplete
     * @param reason why {@code rates} is {@code null}, or {@code null} if it isn't
     */
    private record RangeResolution(List<CityTaxRate> rates, CityTaxUnassessedReason reason) {
    }
}
