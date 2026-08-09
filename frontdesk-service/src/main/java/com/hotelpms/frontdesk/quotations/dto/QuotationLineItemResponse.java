package com.hotelpms.frontdesk.quotations.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for a single room within a quotation.
 *
 * @param id           the line item id
 * @param roomId       the room id
 * @param roomNumber   the room's display number, resolved from {@code
 *                     RoomService} at read time (not persisted on the line item)
 * @param roomTypeName the room's type name, resolved the same way
 * @param price        the resolved price for this room across the whole stay
 */
public record QuotationLineItemResponse(
        UUID id, UUID roomId, String roomNumber, String roomTypeName, BigDecimal price) {
}
