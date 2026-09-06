package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for transferring a charge onto a reservation group's master folio,
 * tagging which stay it originally belonged to (billing-service's {@code
 * invoice_charges.routed_from_stay_id}) so the master folio's line items stay
 * traceable back to the room they came from.
 *
 * @param type             the charge category, e.g. {@code ROOM_NIGHT}
 * @param description      human-readable description of the charge
 * @param amount           the charge amount
 * @param unitPrice        optional per-night price, for display/audit only
 * @param nights           optional number of nights this charge covers, for display/audit only
 * @param routedFromStayId the stay this charge is being transferred from
 */
public record GroupChargeRequest(
        String type, String description, BigDecimal amount,
        BigDecimal unitPrice, Integer nights, UUID routedFromStayId) {
}
