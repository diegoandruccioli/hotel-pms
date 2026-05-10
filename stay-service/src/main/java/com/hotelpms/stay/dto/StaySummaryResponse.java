package com.hotelpms.stay.dto;

import com.hotelpms.stay.domain.StayStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Minimal stay summary for GDPR Art. 20 data-export.
 * Contains only the fields relevant to the data subject's right of portability.
 *
 * @param stayId       the stay UUID
 * @param checkInTime  the actual check-in timestamp
 * @param checkOutTime the actual check-out timestamp (null if still active)
 * @param roomId       the room UUID
 * @param status       the stay status
 */
public record StaySummaryResponse(
        UUID stayId,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        UUID roomId,
        StayStatus status) {
}
