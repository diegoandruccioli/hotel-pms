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
 * @param totalPrice         the lowest {@code totalPrice} across {@code options} — used
 *                           for list sorting/display; each option carries its own total
 * @param options            the alternative room combinations offered (1-5)
 * @param acceptedOptionId   the option accepted at conversion time, or {@code null}
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
        List<QuotationOptionResponse> options,
        UUID acceptedOptionId,
        boolean sendFailed,
        String sendFailureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Compact constructor — defensive copy of the options list.
     */
    public QuotationResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /**
     * Returns a copy of the options list to prevent external modification.
     *
     * @return the alternative room combinations offered
     */
    @Override
    public List<QuotationOptionResponse> options() {
        return List.copyOf(options);
    }
}
