package com.hotelpms.frontdesk.stays.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Data Transfer Object for extending an open stay's expected check-out date (Parte 3).
 *
 * @param newCheckOutDate the new expected check-out date; must be strictly after the
 *                        stay's current one
 * @param version         the version the client last read, for optimistic-lock
 *                        conflict detection; {@code null} skips the check
 *                        (backward-compatible for callers that don't send it yet)
 */
public record StayExtensionRequest(
        @NotNull(message = "New check-out date is required") LocalDate newCheckOutDate,
        Long version
) {
}
