package com.hotelpms.guest.dto.response;

import java.time.LocalDate;

/**
 * Response body for HTTP 451 Unavailable For Legal Reasons.
 * Returned when a guest hard-delete is blocked by an active legal hold.
 *
 * @param code        machine-readable error code ({@code LEGAL_HOLD_ACTIVE})
 * @param detail      human-readable explanation
 * @param unlocksAt   the earliest date at which deletion becomes permissible
 * @param legalBasis  which obligation is blocking deletion
 *                    ({@code TULPS}, {@code FISCAL}, or {@code TULPS_AND_FISCAL})
 */
public record GdprLegalHoldResponse(
        String code,
        String detail,
        LocalDate unlocksAt,
        String legalBasis) {
}
