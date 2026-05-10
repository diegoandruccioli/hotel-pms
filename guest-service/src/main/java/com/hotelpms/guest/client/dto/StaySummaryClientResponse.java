package com.hotelpms.guest.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feign client DTO mirroring {@code StaySummaryResponse} from stay-service.
 * Used exclusively by the GDPR Art. 20 data-export.
 *
 * @param stayId       the stay UUID
 * @param checkInTime  the actual check-in timestamp
 * @param checkOutTime the actual check-out timestamp (null if still active)
 * @param roomId       the room UUID
 * @param status       the stay status as a string
 */
public record StaySummaryClientResponse(
        UUID stayId,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        UUID roomId,
        String status) {
}
