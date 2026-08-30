package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentLineResult;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CityTaxCalculatorTest {

    private static final LocalDate FIRST_NIGHT = LocalDate.of(2026, 8, 10);
    private static final LocalDate RATE_VALID_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate ADULT_DATE_OF_BIRTH = LocalDate.of(1990, 1, 1);
    private static final BigDecimal AMOUNT_PER_NIGHT = new BigDecimal("2.50");
    private static final int MAX_TAXABLE_NIGHTS = 7;
    private static final int EXEMPT_UNDER_AGE_14 = 14;
    private static final int EXEMPT_UNDER_AGE_18 = 18;
    private static final int NIGHTS_UNDER_CAP = 3;
    private static final int NIGHTS_OVER_CAP = 10;
    private static final int NIGHTS_UNCAPPED = 30;
    private static final int ONE_NIGHT = 1;
    private static final int TWO_NIGHTS = 2;
    private static final int SCALE_TWO = 2;
    private static final LocalDate PRIOR_YEAR_VALID_FROM = LocalDate.of(2025, 1, 1);
    private static final int CROSSOVER_NIGHTS = 5;

    private final CityTaxCalculator calculator = new CityTaxCalculator();

    private CityTaxRate.CityTaxRateBuilder rateBuilder;

    @BeforeEach
    void setUp() {
        rateBuilder = CityTaxRate.builder()
                .id(UUID.randomUUID())
                .hotelId(UUID.randomUUID())
                .comuneCodice("099014")
                .category("4_STAR")
                .amountPerNight(AMOUNT_PER_NIGHT)
                .validFrom(RATE_VALID_FROM);
    }

    private static StayGuest guestBornOn(final LocalDate dateOfBirth) {
        return StayGuest.builder().id(UUID.randomUUID()).dateOfBirth(dateOfBirth).build();
    }

    @Test
    void nightsUnderCapAreAllTaxable() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(MAX_TAXABLE_NIGHTS).build();

        final CityTaxAssessmentResult result = calculator.assess(
                List.of(rate), FIRST_NIGHT, NIGHTS_UNDER_CAP, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(NIGHTS_UNDER_CAP, result.taxableNights());
        assertEquals(new BigDecimal("7.50"), result.totalAmount());
        assertEquals(1, result.lines().size());
    }

    @Test
    void nightsAtExactCapAreAllTaxable() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(MAX_TAXABLE_NIGHTS).build();

        final CityTaxAssessmentResult result = calculator.assess(
                List.of(rate), FIRST_NIGHT, MAX_TAXABLE_NIGHTS, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(MAX_TAXABLE_NIGHTS, result.taxableNights());
    }

    @Test
    void nightsOverCapAreClampedToCap() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(MAX_TAXABLE_NIGHTS).build();

        final CityTaxAssessmentResult result = calculator.assess(
                List.of(rate), FIRST_NIGHT, NIGHTS_OVER_CAP, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(MAX_TAXABLE_NIGHTS, result.taxableNights());
    }

    @Test
    void nullMaxTaxableNightsMeansUncapped() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(null).build();

        final CityTaxAssessmentResult result = calculator.assess(
                List.of(rate), FIRST_NIGHT, NIGHTS_UNCAPPED, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(NIGHTS_UNCAPPED, result.taxableNights());
    }

    @Test
    void guestWithBirthdayOnCheckInDayIsAlreadyOfExemptionAge() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_14).build();
        final LocalDate turns14Today = FIRST_NIGHT.minusYears(EXEMPT_UNDER_AGE_14);

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(rate), FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(turns14Today)));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void guestTurningExemptionAgeTheDayAfterCheckInIsStillExempt() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_14).build();
        final LocalDate turns14Tomorrow = FIRST_NIGHT.minusYears(EXEMPT_UNDER_AGE_14).plusDays(1);

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(rate), FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(turns14Tomorrow)));

        assertEquals(0, result.taxableGuests());
        assertEquals(BigDecimal.ZERO.setScale(SCALE_TWO), result.totalAmount());
    }

    @Test
    void guestWhoTurnedExemptionAgeTheDayBeforeCheckInIsTaxable() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_14).build();
        final LocalDate turned14Yesterday = FIRST_NIGHT.minusYears(EXEMPT_UNDER_AGE_14).minusDays(1);

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(rate), FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(turned14Yesterday)));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void leapDayBirthdateDoesNotThrowOnNonLeapCheckInYear() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_18).build();
        final LocalDate leapBirthdate = LocalDate.of(2008, 2, 29);
        final StayGuest guest = guestBornOn(leapBirthdate);
        final LocalDate nonLeapCheckIn = LocalDate.of(2026, 3, 1);

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(rate), nonLeapCheckIn, ONE_NIGHT, List.of(guest));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void nullExemptUnderAgeMeansNoAgeExemption() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(null).build();
        final StayGuest infant = guestBornOn(FIRST_NIGHT.minusDays(1));

        final CityTaxAssessmentResult result = calculator.assess(List.of(rate), FIRST_NIGHT, ONE_NIGHT, List.of(infant));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void allGuestsExemptYieldsZeroTotal() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_18).build();
        final List<StayGuest> guests = List.of(
                guestBornOn(FIRST_NIGHT.minusYears(5)), guestBornOn(FIRST_NIGHT.minusYears(10)));

        final CityTaxAssessmentResult result = calculator.assess(List.of(rate), FIRST_NIGHT, TWO_NIGHTS, guests);

        assertEquals(0, result.taxableGuests());
        assertEquals(BigDecimal.ZERO.setScale(SCALE_TWO), result.totalAmount());
    }

    @Test
    void totalRoundsHalfUpToTwoDecimals() {
        final CityTaxRate rate = rateBuilder.amountPerNight(new BigDecimal("1.005")).build();

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(rate), FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(new BigDecimal("1.01"), result.totalAmount());
    }

    // -----------------------------------------------------------------
    // Parte 6 — a stay crossing a delibera boundary must be taxed at
    // whichever rate was actually in effect each night, not one rate
    // applied to the whole stay.
    // -----------------------------------------------------------------

    @Test
    void stayCrossingANewYearRateChangeIsTaxedAtEachSegmentsOwnRate() {
        // 29 Dec - 3 Jan: the classic Capodanno case the plan calls out by name.
        final LocalDate checkIn = LocalDate.of(2025, 12, 29);
        final int nights = CROSSOVER_NIGHTS;
        final LocalDate splitDate = LocalDate.of(2026, 1, 1);
        final CityTaxRate oldRate = rateBuilder
                .amountPerNight(new BigDecimal("2.00")).validFrom(PRIOR_YEAR_VALID_FROM).validTo(splitDate).build();
        final CityTaxRate newRate = rateBuilder
                .id(UUID.randomUUID()).amountPerNight(new BigDecimal("3.00")).validFrom(splitDate).validTo(null).build();
        final List<StayGuest> guests = List.of(guestBornOn(ADULT_DATE_OF_BIRTH));

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(oldRate, newRate), checkIn, nights, guests);

        assertEquals(2, result.lines().size());
        final CityTaxAssessmentLineResult oldSegment = result.lines().get(0);
        assertEquals(checkIn, oldSegment.fromDate());
        assertEquals(splitDate, oldSegment.toDateExclusive());
        assertEquals(3, oldSegment.taxableNights()); // 29, 30, 31 Dec
        assertEquals(new BigDecimal("6.00"), oldSegment.subtotal());

        final CityTaxAssessmentLineResult newSegment = result.lines().get(1);
        assertEquals(splitDate, newSegment.fromDate());
        assertEquals(checkIn.plusDays(nights), newSegment.toDateExclusive());
        assertEquals(2, newSegment.taxableNights()); // 1, 2 Jan
        assertEquals(new BigDecimal("6.00"), newSegment.subtotal());

        assertEquals(CROSSOVER_NIGHTS, result.taxableNights());
        assertEquals(new BigDecimal("12.00"), result.totalAmount());
    }

    @Test
    void rateOrderInTheInputListDoesNotAffectTheResult() {
        final LocalDate checkIn = LocalDate.of(2025, 12, 29);
        final LocalDate splitDate = LocalDate.of(2026, 1, 1);
        final CityTaxRate oldRate = rateBuilder
                .amountPerNight(new BigDecimal("2.00")).validFrom(PRIOR_YEAR_VALID_FROM).validTo(splitDate).build();
        final CityTaxRate newRate = rateBuilder
                .id(UUID.randomUUID()).amountPerNight(new BigDecimal("3.00")).validFrom(splitDate).validTo(null).build();
        final List<StayGuest> guests = List.of(guestBornOn(ADULT_DATE_OF_BIRTH));

        // Deliberately reversed order — assess() must not depend on input ordering.
        final CityTaxAssessmentResult result = calculator.assess(List.of(newRate, oldRate), checkIn, CROSSOVER_NIGHTS, guests);

        assertEquals(new BigDecimal("12.00"), result.totalAmount());
        assertEquals(checkIn, result.lines().get(0).fromDate());
    }

    @Test
    void eachSegmentAppliesItsOwnRatesMaxTaxableNightsCapIndependently() {
        final LocalDate checkIn = LocalDate.of(2025, 12, 27);
        final LocalDate splitDate = LocalDate.of(2026, 1, 1);
        // Old rate: 5 nights available (27-31 Dec), capped at 2 -> only 2 taxed.
        final CityTaxRate oldRate = rateBuilder
                .maxTaxableNights(TWO_NIGHTS).validFrom(PRIOR_YEAR_VALID_FROM).validTo(splitDate).build();
        // New rate: 3 nights available (1-3 Jan), uncapped -> all 3 taxed.
        final CityTaxRate newRate = rateBuilder
                .id(UUID.randomUUID()).maxTaxableNights(null).validFrom(splitDate).validTo(null).build();
        final List<StayGuest> guests = List.of(guestBornOn(ADULT_DATE_OF_BIRTH));

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(oldRate, newRate), checkIn, NIGHTS_UNDER_CAP + CROSSOVER_NIGHTS, guests);

        assertEquals(TWO_NIGHTS, result.lines().get(0).taxableNights());
        assertEquals(3, result.lines().get(1).taxableNights());
        assertEquals(CROSSOVER_NIGHTS, result.taxableNights()); // capped independently per segment, not one stay-wide cap
    }

    @Test
    void exemptionThresholdIsEvaluatedPerSegmentsOwnRate() {
        // Guest is 15 at first night: exempt under the old rate's threshold,
        // taxable under the new rate's threshold — a rate change, not an age
        // change, is what flips this.
        final LocalDate checkIn = LocalDate.of(2025, 12, 30);
        final LocalDate splitDate = LocalDate.of(2026, 1, 1);
        final CityTaxRate oldRate = rateBuilder
                .exemptUnderAge(EXEMPT_UNDER_AGE_18).validFrom(PRIOR_YEAR_VALID_FROM).validTo(splitDate).build();
        final CityTaxRate newRate = rateBuilder
                .id(UUID.randomUUID()).exemptUnderAge(EXEMPT_UNDER_AGE_14).validFrom(splitDate).validTo(null).build();
        final StayGuest fifteenYearOld = guestBornOn(checkIn.minusYears(15));

        final CityTaxAssessmentResult result =
                calculator.assess(List.of(oldRate, newRate), checkIn, 4, List.of(fifteenYearOld));

        assertEquals(0, result.lines().get(0).taxableGuests()); // exempt under old rate's 18 threshold
        assertEquals(1, result.lines().get(1).taxableGuests()); // taxable under new rate's 14 threshold
    }

    @Test
    void throwsWhenTheGivenRatesDoNotFullyCoverTheStay() {
        // assess() trusts its caller to have already verified full coverage
        // (CityTaxAssessmentServiceImpl#resolveRange) — a gap here is a caller bug,
        // not a legitimate "no rate configured" outcome for assess() to swallow.
        final CityTaxRate onlyCoversFirstNight = rateBuilder.validFrom(FIRST_NIGHT).validTo(FIRST_NIGHT.plusDays(1)).build();

        assertThrows(IllegalStateException.class, () -> calculator.assess(
                List.of(onlyCoversFirstNight), FIRST_NIGHT, TWO_NIGHTS, List.of(guestBornOn(ADULT_DATE_OF_BIRTH))));
    }
}
