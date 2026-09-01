package com.hotelpms.frontdesk.citytax.dto;

import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;

import java.time.LocalDateTime;

/**
 * Summary of stays whose tourist tax was never assessed because of a
 * configuration gap ({@code NOT_APPLICABLE} excluded — that's a deliberate
 * hotel declaration, not a gap). Drives the Dashboard alert banner, same
 * pattern as {@code AlloggiatiFailureSummaryResponse}.
 *
 * @param unassessedCount    how many stays are affected
 * @param mostRecentUnassessedAt the most recent affected stay's assessment timestamp, or
 *                            {@code null} if none
 * @param mostRecentReason    the reason for the most recent one, or {@code null} if none
 */
public record CityTaxUnassessedSummaryResponse(
        int unassessedCount,
        LocalDateTime mostRecentUnassessedAt,
        CityTaxUnassessedReason mostRecentReason) {
}
