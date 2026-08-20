package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                rate, FIRST_NIGHT, NIGHTS_UNDER_CAP, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(NIGHTS_UNDER_CAP, result.taxableNights());
        assertEquals(new BigDecimal("7.50"), result.totalAmount());
    }

    @Test
    void nightsAtExactCapAreAllTaxable() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(MAX_TAXABLE_NIGHTS).build();

        final CityTaxAssessmentResult result = calculator.assess(
                rate, FIRST_NIGHT, MAX_TAXABLE_NIGHTS, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(MAX_TAXABLE_NIGHTS, result.taxableNights());
    }

    @Test
    void nightsOverCapAreClampedToCap() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(MAX_TAXABLE_NIGHTS).build();

        final CityTaxAssessmentResult result = calculator.assess(
                rate, FIRST_NIGHT, NIGHTS_OVER_CAP, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(MAX_TAXABLE_NIGHTS, result.taxableNights());
    }

    @Test
    void nullMaxTaxableNightsMeansUncapped() {
        final CityTaxRate rate = rateBuilder.maxTaxableNights(null).build();

        final CityTaxAssessmentResult result = calculator.assess(
                rate, FIRST_NIGHT, NIGHTS_UNCAPPED, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(NIGHTS_UNCAPPED, result.taxableNights());
    }

    @Test
    void guestWithBirthdayOnCheckInDayIsAlreadyOfExemptionAge() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_14).build();
        final LocalDate turns14Today = FIRST_NIGHT.minusYears(EXEMPT_UNDER_AGE_14);

        final CityTaxAssessmentResult result =
                calculator.assess(rate, FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(turns14Today)));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void guestTurningExemptionAgeTheDayAfterCheckInIsStillExempt() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_14).build();
        final LocalDate turns14Tomorrow = FIRST_NIGHT.minusYears(EXEMPT_UNDER_AGE_14).plusDays(1);

        final CityTaxAssessmentResult result =
                calculator.assess(rate, FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(turns14Tomorrow)));

        assertEquals(0, result.taxableGuests());
        assertEquals(BigDecimal.ZERO.setScale(SCALE_TWO), result.totalAmount());
    }

    @Test
    void guestWhoTurnedExemptionAgeTheDayBeforeCheckInIsTaxable() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_14).build();
        final LocalDate turned14Yesterday = FIRST_NIGHT.minusYears(EXEMPT_UNDER_AGE_14).minusDays(1);

        final CityTaxAssessmentResult result =
                calculator.assess(rate, FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(turned14Yesterday)));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void leapDayBirthdateDoesNotThrowOnNonLeapCheckInYear() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_18).build();
        final LocalDate leapBirthdate = LocalDate.of(2008, 2, 29);
        final StayGuest guest = guestBornOn(leapBirthdate);
        final LocalDate nonLeapCheckIn = LocalDate.of(2026, 3, 1);

        final CityTaxAssessmentResult result = calculator.assess(rate, nonLeapCheckIn, ONE_NIGHT, List.of(guest));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void nullExemptUnderAgeMeansNoAgeExemption() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(null).build();
        final StayGuest infant = guestBornOn(FIRST_NIGHT.minusDays(1));

        final CityTaxAssessmentResult result = calculator.assess(rate, FIRST_NIGHT, ONE_NIGHT, List.of(infant));

        assertEquals(1, result.taxableGuests());
    }

    @Test
    void allGuestsExemptYieldsZeroTotal() {
        final CityTaxRate rate = rateBuilder.exemptUnderAge(EXEMPT_UNDER_AGE_18).build();
        final List<StayGuest> guests = List.of(
                guestBornOn(FIRST_NIGHT.minusYears(5)), guestBornOn(FIRST_NIGHT.minusYears(10)));

        final CityTaxAssessmentResult result = calculator.assess(rate, FIRST_NIGHT, TWO_NIGHTS, guests);

        assertEquals(0, result.taxableGuests());
        assertEquals(BigDecimal.ZERO.setScale(SCALE_TWO), result.totalAmount());
    }

    @Test
    void totalRoundsHalfUpToTwoDecimals() {
        final CityTaxRate rate = rateBuilder.amountPerNight(new BigDecimal("1.005")).build();

        final CityTaxAssessmentResult result =
                calculator.assess(rate, FIRST_NIGHT, ONE_NIGHT, List.of(guestBornOn(ADULT_DATE_OF_BIRTH)));

        assertEquals(new BigDecimal("1.01"), result.totalAmount());
    }
}
