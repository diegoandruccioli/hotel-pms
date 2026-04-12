package com.hotelpms.reservation.validation;

import com.hotelpms.reservation.domain.ReservationStatus;
import com.hotelpms.reservation.dto.ReservationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DateRangeValidator} (T-RES-03).
 *
 * <p>Verifies that the cross-field constraint correctly accepts valid date ranges
 * and rejects zero-night stays or inverted ranges.
 */
class DateRangeValidatorTest {

    private DateRangeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DateRangeValidator();
    }

    @Test
    void shouldPassWhenCheckOutIsAfterCheckIn() {
        final ReservationRequest request = buildRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3));

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldFailWhenCheckOutSameDayAsCheckIn() {
        // zero-night stay: checkout == checkin
        final LocalDate sameDay = LocalDate.now().plusDays(2);
        final ReservationRequest request = buildRequest(sameDay, sameDay);

        assertFalse(validator.isValid(request, null));
    }

    @Test
    void shouldFailWhenCheckOutBeforeCheckIn() {
        // inverted range: checkout < checkin
        final ReservationRequest request = buildRequest(
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(2));

        assertFalse(validator.isValid(request, null));
    }

    @Test
    void shouldPassWhenCheckInDateIsNull() {
        // null handling delegated to @NotNull — validator must not throw
        final ReservationRequest request = buildRequest(null, LocalDate.now().plusDays(3));

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldPassWhenCheckOutDateIsNull() {
        // null handling delegated to @NotNull — validator must not throw
        final ReservationRequest request = buildRequest(LocalDate.now().plusDays(1), null);

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldPassWhenRequestIsNull() {
        assertTrue(validator.isValid(null, null));
    }

    private static ReservationRequest buildRequest(
            final LocalDate checkIn, final LocalDate checkOut) {
        return new ReservationRequest(
                UUID.randomUUID(),
                2,
                checkIn,
                checkOut,
                ReservationStatus.CONFIRMED,
                List.of());
    }
}
