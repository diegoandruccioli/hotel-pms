package com.hotelpms.reservation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field constraint enforcing that {@code checkOutDate} is strictly after
 * {@code checkInDate} in a {@link com.hotelpms.reservation.dto.ReservationRequest}.
 *
 * <p>If either date is {@code null}, this constraint passes (null-checking is
 * delegated to the individual {@code @NotNull} field annotations).
 *
 * <p>A request where {@code checkOutDate == checkInDate} (zero-night stay) or
 * {@code checkOutDate < checkInDate} (inverted range) could otherwise bypass the
 * overlap-detection query and corrupt reservation data. This annotation prevents
 * both cases at the controller boundary before the request reaches any service logic.
 */
@Documented
@Constraint(validatedBy = DateRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {

    /**
     * Constraint violation message.
     *
     * @return message
     */
    String message() default "checkOutDate must be strictly after checkInDate";

    /**
     * Validation groups.
     *
     * @return groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload.
     *
     * @return payload
     */
    Class<? extends Payload>[] payload() default {};
}
