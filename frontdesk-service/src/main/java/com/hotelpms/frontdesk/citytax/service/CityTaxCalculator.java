package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic tourist-tax calculation for one stay against the rate(s) in
 * effect during it — a pure function of its inputs, no repository access, so
 * it is fully unit-testable without a database.
 *
 * <p>A stay's nights can cross a delibera boundary (comuni typically change
 * rates on 1 January or 1 April — exactly when stays commonly span the
 * boundary, e.g. Capodanno or Pasqua), so this walks the stay night by night
 * and taxes each one at whichever rate was actually in effect that night,
 * grouping consecutive same-rate nights into one line each. {@code rates}
 * must fully cover {@code [firstNight, firstNight + nights)} with no gaps —
 * the caller ({@code CityTaxAssessmentServiceImpl}) is responsible for that
 * check before calling this, since "no rate for this range" is a distinct
 * unassessed reason, not a calculator failure.
 *
 * <p>Age is evaluated once, at the stay's first night — not per-night, and
 * not re-evaluated at check-out. This matches the immutable-snapshot model
 * ({@code CityTaxAssessment}) and the existing convention for
 * {@code ROOM_NIGHT} (assessed once at check-in, never adjusted for an early
 * or late departure). Some comune regolamenti evaluate age per-night instead
 * (a guest turning the exemption age mid-stay); first-night is the
 * deterministic v1 choice — revisit only if a specific comune's rules
 * require otherwise. Each segment's exemption threshold is still its own
 * rate's {@code exemptUnderAge}, evaluated against that fixed first-night age
 * — a rate change mid-stay can change who's exempt, an age change cannot.
 *
 * <p>{@code maxTaxableNights} is applied per segment, not once across the
 * whole stay: a cap belongs to the regolamento that set it, and two
 * consecutive rates are not obligated to share the same cap policy. A stay
 * spanning two rates can therefore have each segment capped independently.
 */
@Component
public class CityTaxCalculator {

    /**
     * Computes the tourist tax for a stay against every rate in effect during it.
     *
     * @param rates      every rate covering the stay's nights, in any order —
     *                   must fully cover {@code [firstNight, firstNight + nights)}
     * @param firstNight the stay's first night (age exemption is evaluated as of this date)
     * @param nights     the number of nights to assess (same value already computed
     *                   for the {@code ROOM_NIGHT} charge — never recalculated here)
     * @param guests     the stay's guests
     * @return the assessment result, one line per homogeneous-rate segment
     */
    public CityTaxAssessmentResult assess(
            final List<CityTaxRate> rates, final LocalDate firstNight, final long nights,
            final List<StayGuest> guests) {
        final LocalDate stayEnd = firstNight.plusDays(nights);
        final List<CityTaxAssessmentLineResult> lines = new ArrayList<>();

        LocalDate segmentStart = firstNight;
        while (segmentStart.isBefore(stayEnd)) {
            final CityTaxRate rate = rateCovering(rates, segmentStart);
            if (rate == null) {
                // Caller contract violation — resolveRange must guarantee full coverage
                // before calling assess(). Fail loudly rather than silently under-tax.
                throw new IllegalStateException("No rate covers " + segmentStart + " — caller must pre-validate coverage");
            }
            LocalDate segmentEnd = segmentStart.plusDays(1);
            while (segmentEnd.isBefore(stayEnd) && rate.equals(rateCovering(rates, segmentEnd))) {
                segmentEnd = segmentEnd.plusDays(1);
            }
            lines.add(buildLine(rate, segmentStart, segmentEnd, firstNight, guests));
            segmentStart = segmentEnd;
        }

        return aggregate(lines);
    }

    private CityTaxAssessmentLineResult buildLine(
            final CityTaxRate rate, final LocalDate segmentStart, final LocalDate segmentEnd,
            final LocalDate firstNight, final List<StayGuest> guests) {
        final int rawNights = (int) ChronoUnit.DAYS.between(segmentStart, segmentEnd);
        final int taxableNights = rate.getMaxTaxableNights() == null
                ? rawNights
                : Math.min(rawNights, rate.getMaxTaxableNights());

        final long taxableGuests = guests.stream()
                .filter(guest -> !isExempt(guest, rate, firstNight))
                .count();

        final BigDecimal subtotal = rate.getAmountPerNight()
                .multiply(BigDecimal.valueOf(taxableGuests))
                .multiply(BigDecimal.valueOf(taxableNights))
                .setScale(2, RoundingMode.HALF_UP);

        return new CityTaxAssessmentLineResult(
                segmentStart, segmentEnd, rate.getId(), rate.getAmountPerNight(),
                (int) taxableGuests, taxableNights, subtotal);
    }

    /**
     * Aggregates per-segment lines into the overall result. {@code taxableGuests}
     * on the aggregate reflects the first segment only (matching the "evaluated
     * once, at the stay's first night" convention for the top-level display
     * fields) — the per-segment truth, including any difference caused by a
     * rate change mid-stay, is always in {@code lines()}.
     *
     * @param lines the per-segment results, in stay order; never empty
     * @return the aggregate result
     */
    private static CityTaxAssessmentResult aggregate(final List<CityTaxAssessmentLineResult> lines) {
        final int totalTaxableNights = lines.stream().mapToInt(CityTaxAssessmentLineResult::taxableNights).sum();
        final int firstSegmentGuests = lines.get(0).taxableGuests();
        final BigDecimal total = lines.stream()
                .map(CityTaxAssessmentLineResult::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CityTaxAssessmentResult(totalTaxableNights, firstSegmentGuests, total, lines);
    }

    private static CityTaxRate rateCovering(final List<CityTaxRate> rates, final LocalDate date) {
        return rates.stream()
                .filter(rate -> !rate.getValidFrom().isAfter(date)
                        && (rate.getValidTo() == null || rate.getValidTo().isAfter(date)))
                .findFirst()
                .orElse(null);
    }

    private static boolean isExempt(final StayGuest guest, final CityTaxRate rate, final LocalDate firstNight) {
        if (rate.getExemptUnderAge() == null) {
            return false;
        }
        final int age = Period.between(guest.getDateOfBirth(), firstNight).getYears();
        return age < rate.getExemptUnderAge();
    }

    /**
     * The result of a tourist-tax calculation.
     *
     * @param taxableNights the total number of nights actually taxed across every segment
     * @param taxableGuests the taxable-guest count for the first segment (see {@link
     *                      #aggregate}) — the true per-segment counts are in {@code lines}
     * @param totalAmount   the total tax amount, the sum of every line's {@code subtotal}
     * @param lines         one entry per homogeneous-rate segment, in stay order
     */
    public record CityTaxAssessmentResult(
            int taxableNights, int taxableGuests, BigDecimal totalAmount,
            List<CityTaxAssessmentLineResult> lines) {

        /**
         * Defensive copy of the mutable list.
         *
         * @param taxableNights the total number of nights actually taxed across every segment
         * @param taxableGuests the taxable-guest count for the first segment
         * @param totalAmount   the total tax amount
         * @param lines         one entry per homogeneous-rate segment, in stay order
         */
        public CityTaxAssessmentResult {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        /**
         * Returns a defensive copy of the lines.
         *
         * @return the per-segment lines
         */
        @Override
        public List<CityTaxAssessmentLineResult> lines() {
            return List.copyOf(lines);
        }
    }

    /**
     * One homogeneous-rate segment of a tourist-tax calculation.
     *
     * @param fromDate       segment start (inclusive)
     * @param toDateExclusive segment end (exclusive) — the night before is the last taxed night
     * @param cityTaxRateId  the rate actually in effect for this segment
     * @param amountPerNight that rate's per-night amount
     * @param taxableGuests  guests not exempt under this rate's {@code exemptUnderAge}
     * @param taxableNights  nights actually taxed in this segment, after this rate's own cap
     * @param subtotal       {@code amountPerNight × taxableGuests × taxableNights}
     */
    public record CityTaxAssessmentLineResult(
            LocalDate fromDate, LocalDate toDateExclusive, UUID cityTaxRateId, BigDecimal amountPerNight,
            int taxableGuests, int taxableNights, BigDecimal subtotal) {
    }
}
