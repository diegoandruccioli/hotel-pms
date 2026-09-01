package com.hotelpms.frontdesk.stays.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Data Transfer Object for recording a guest's early departure.
 *
 * @param departureDate the date the guest actually left
 */
public record StayGuestDepartureRequest(
        @NotNull(message = "Departure date is required") LocalDate departureDate
) {
}
