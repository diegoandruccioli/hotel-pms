package com.hotelpms.frontdesk.reservations.dto;

import java.util.UUID;

/**
 * The group-billing facts {@code StayBillingCoordinator} needs at check-out to
 * decide whether a room's charges should transfer to a master folio (Punto 4) --
 * deliberately not part of {@link ReservationResponse}, to avoid widening that
 * record (and every one of its many test call sites) for a field only the
 * check-out path needs.
 *
 * @param groupId             the reservation's group id
 * @param billedToMasterFolio whether this room's charges route to the group's
 *                            master folio at check-out
 */
public record ReservationGroupBillingInfo(UUID groupId, boolean billedToMasterFolio) {
}
