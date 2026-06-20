package com.hotelpms.frontdesk.reservations.validation;

import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that the {@code checkOutDate} in a
 * {@link com.hotelpms.frontdesk.reservations.dto.ReservationRequest} is strictly after
 * {@code checkInDate}.
 *
 * <p>Returns {@code true} (valid) when either date is {@code null}: the
 * {@code @NotNull} field constraints on the DTO are responsible for rejecting
 * missing dates; this validator only enforces logical consistency between them.
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, ReservationRequest> {

    /** {@inheritDoc} */
    @Override
    public boolean isValid(final ReservationRequest request,
            final ConstraintValidatorContext context) {
        if (request == null
                || request.checkInDate() == null
                || request.checkOutDate() == null) {
            return true;
        }
        return request.checkOutDate().isAfter(request.checkInDate());
    }
}
