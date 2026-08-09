package com.hotelpms.frontdesk.quotations.dto;

import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a {@code Quotation}.
 *
 * @param id                 the quotation id
 * @param guestId            the guest id, or {@code null} for a prospect
 * @param guestFullName      resolved display name — the guest's full name, or
 *                           the prospect's first+last name
 * @param prospectEmail      the prospect's email; {@code null} when {@code guestId} is set
 * @param checkInDate        the check-in date
 * @param checkOutDate       the check-out date
 * @param expectedGuests     expected number of guests
 * @param status             the effective status — {@code EXPIRED} is computed here,
 *                           never persisted (see {@code Quotation#isExpired})
 * @param validUntil         the last date this quotation is honored at its quoted price
 * @param totalPrice         the total price across all line items
 * @param lineItems          the priced rooms
 * @param sendFailed         whether the last email-send attempt failed
 * @param sendFailureReason  the reason for the last failed send, if any
 * @param createdAt          creation timestamp
 * @param updatedAt          last update timestamp
 */
public record QuotationResponse(
        UUID id,
        UUID guestId,
        String guestFullName,
        String prospectEmail,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer expectedGuests,
        QuotationStatus status,
        LocalDate validUntil,
        BigDecimal totalPrice,
        List<QuotationLineItemResponse> lineItems,
        boolean sendFailed,
        String sendFailureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Compact constructor — defensive copy of the line items list.
     */
    public QuotationResponse {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    /**
     * Returns a copy of the line items list to prevent external modification.
     *
     * @return the priced rooms
     */
    @Override
    public List<QuotationLineItemResponse> lineItems() {
        return List.copyOf(lineItems);
    }
}
