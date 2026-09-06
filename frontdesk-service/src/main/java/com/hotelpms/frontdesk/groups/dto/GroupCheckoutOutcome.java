package com.hotelpms.frontdesk.groups.dto;

import java.util.UUID;

/**
 * Per-room outcome of a bulk group check-out ({@code
 * ReservationGroupService#checkoutGroup}): one room's unpaid F&amp;B extras (or
 * any other checkout guard) must not block the rest of the group from checking
 * out, so the action tries every eligible stay and reports each result rather
 * than failing the whole request on the first error.
 *
 * @param reservationId the room's reservation id
 * @param stayId        the room's stay id, or {@code null} if it was never checked in
 * @param success       whether this room's check-out succeeded
 * @param errorCode     the failure reason (e.g. {@code BILLING_NOT_PAID}), or
 *                      {@code null} on success
 */
public record GroupCheckoutOutcome(UUID reservationId, UUID stayId, boolean success, String errorCode) {
}
