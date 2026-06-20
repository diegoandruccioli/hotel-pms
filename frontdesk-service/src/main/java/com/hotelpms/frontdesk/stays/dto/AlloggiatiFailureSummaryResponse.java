package com.hotelpms.frontdesk.stays.dto;

import java.time.LocalDateTime;

/**
 * Summary of unresolved Alloggiati Web submission failures for the caller's hotel,
 * used to drive the Dashboard alert banner.
 *
 * @param failedCount             how many stays currently have an unresolved failed submission
 * @param mostRecentFailureAt     the check-in time of the most recently failed stay, or null
 * @param mostRecentFailureReason the error message from that failure, or null
 */
public record AlloggiatiFailureSummaryResponse(
        long failedCount,
        LocalDateTime mostRecentFailureAt,
        String mostRecentFailureReason) {
}
