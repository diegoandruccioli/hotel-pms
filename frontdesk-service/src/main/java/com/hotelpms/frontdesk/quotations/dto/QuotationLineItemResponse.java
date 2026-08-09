package com.hotelpms.frontdesk.quotations.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for a single room within a quotation.
 *
 * @param id     the line item id
 * @param roomId the room id
 * @param price  the resolved price for this room across the whole stay
 */
public record QuotationLineItemResponse(UUID id, UUID roomId, BigDecimal price) {
}
