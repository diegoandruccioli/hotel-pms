package com.hotelpms.frontdesk.reservations.dto;

import java.util.UUID;

/**
 * The group-billing facts {@code StayBillingCoordinator} needs at check-out to
 * decide whether a room's charges should transfer to a master folio (Punto 4).
 * {@link ReservationResponse} carries only the group id (so the UI can link back
 * to the group); whether the room bills to the master folio stays here, as only
 * the check-out path needs it.
 *
 * @param groupId             the reservation's group id
 * @param billedToMasterFolio whether this room's charges route to the group's
 *                            master folio at check-out
 */
public record ReservationGroupBillingInfo(UUID groupId, boolean billedToMasterFolio) {
}
