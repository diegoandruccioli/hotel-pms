package com.hotelpms.frontdesk.quotations.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for one option within a quotation.
 *
 * @param id         the option id
 * @param label      the option's display label
 * @param position   display order, 0-based
 * @param totalPrice the sum of this option's line item prices
 * @param lineItems  the rooms this option bundles
 */
public record QuotationOptionResponse(
        UUID id, String label, int position, BigDecimal totalPrice, List<QuotationLineItemResponse> lineItems) {

    /**
     * Defensive copy — a mutable {@code List} field on a record is otherwise a
     * shared-reference leak.
     *
     * @param id         the option id
     * @param label      the option's display label
     * @param position   display order, 0-based
     * @param totalPrice the sum of this option's line item prices
     * @param lineItems  the rooms this option bundles
     */
    public QuotationOptionResponse {
        lineItems = lineItems == null ? null : List.copyOf(lineItems);
    }

    /**
     * Defensive accessor mirroring the compact constructor's copy.
     *
     * @return an unmodifiable copy of the line items
     */
    @Override
    public List<QuotationLineItemResponse> lineItems() {
        return lineItems == null ? null : List.copyOf(lineItems);
    }
}
