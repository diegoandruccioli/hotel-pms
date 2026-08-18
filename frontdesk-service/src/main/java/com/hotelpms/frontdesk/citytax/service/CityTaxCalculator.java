package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Deterministic tourist-tax calculation for one stay against one rate rule —
 * a pure function of its inputs, no repository access, so it is fully
 * unit-testable without a database.
 *
 * <p>Age is evaluated once, at the stay's first night — not per-night, and
 * not re-evaluated at check-out. This matches the immutable-snapshot model
 * ({@code CityTaxAssessment}) and the existing convention for
 * {@code ROOM_NIGHT} (assessed once at check-in, never adjusted for an early
 * or late departure). Some comune regolamenti evaluate age per-night instead
 * (a guest turning the exemption age mid-stay); first-night is the
 * deterministic v1 choice — revisit only if a specific comune's rules
 * require otherwise.
 */
@Component
public class CityTaxCalculator {

    /**
     * Computes the tourist tax for a stay against a rate rule.
     *
     * @param rate      the applicable rate rule
     * @param firstNight the stay's first night (age exemption is evaluated as of this date)
     * @param nights    the number of nights to assess (same value already computed
     *                  for the {@code ROOM_NIGHT} charge — never recalculated here)
     * @param guests    the stay's guests
     * @return the assessment result
     */
    public CityTaxAssessmentResult assess(
            final CityTaxRate rate, final LocalDate firstNight, final long nights, final List<StayGuest> guests) {
        final int taxableNights = rate.getMaxTaxableNights() == null
                ? (int) nights
                : (int) Math.min(nights, rate.getMaxTaxableNights());

        final long taxableGuests = guests.stream()
                .filter(guest -> !isExempt(guest, rate, firstNight))
                .count();

        final BigDecimal total = rate.getAmountPerNight()
                .multiply(BigDecimal.valueOf(taxableGuests))
                .multiply(BigDecimal.valueOf(taxableNights))
                .setScale(2, RoundingMode.HALF_UP);

        return new CityTaxAssessmentResult(taxableNights, (int) taxableGuests, total);
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
     * @param taxableNights the number of nights actually taxed (capped by
     *                      {@code maxTaxableNights}, if any)
     * @param taxableGuests the number of guests actually taxed (age-exempt
     *                      guests excluded)
     * @param totalAmount   the total tax amount, {@code amountPerNight × taxableGuests × taxableNights}
     */
    public record CityTaxAssessmentResult(int taxableNights, int taxableGuests, BigDecimal totalAmount) {
    }
}
