package com.hotelpms.frontdesk.stays.dto;

import com.hotelpms.frontdesk.stays.domain.StayStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Minimal stay summary: the GDPR Art. 20 data-export and the guest sheet's stay history.
 * Contains only the fields relevant to the data subject's right of portability, plus the
 * reservation the stay belongs to so the guest sheet can link to it.
 *
 * @param stayId       the stay UUID
 * @param checkInTime  the actual check-in timestamp
 * @param checkOutTime the actual check-out timestamp (null if still active)
 * @param roomId       the room UUID
 * @param status       the stay status
 * @param reservationId the reservation the stay was checked in from (null for a walk-in)
 */
public record StaySummaryResponse(
        UUID stayId,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        UUID roomId,
        StayStatus status,
        UUID reservationId) {
}
