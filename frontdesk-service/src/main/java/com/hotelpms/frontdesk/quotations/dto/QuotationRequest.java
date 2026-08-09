package com.hotelpms.frontdesk.quotations.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a {@code Quotation}. No price field — the total is
 * always resolved server-side via {@code RatePricingService}, same principle
 * as {@code ReservationLineItemRequest}.
 *
 * <p>Either {@code guestId} or the prospect triple ({@code prospectFirstName}/
 * {@code prospectLastName}/{@code prospectEmail}) must be present — a
 * quotation can be made for a prospect who isn't a persisted {@code Guest} yet.
 *
 * @param guestId           an existing guest, or {@code null} for a prospect
 * @param prospectFirstName prospect's first name; required when {@code guestId} is null
 * @param prospectLastName  prospect's last name; required when {@code guestId} is null
 * @param prospectEmail     prospect's email; required when {@code guestId} is null
 * @param checkInDate       the check-in date (inclusive)
 * @param checkOutDate      the check-out date (exclusive)
 * @param expectedGuests    expected number of guests (optional, informational)
 * @param roomIds           the rooms to price and offer
 * @param validUntil        the last date this quotation is honored at its quoted price
 */
public record QuotationRequest(
        UUID guestId,
        String prospectFirstName,
        String prospectLastName,
        @Email String prospectEmail,

        @NotNull(message = REQUIRED_MSG) @FutureOrPresent(message = "Future") LocalDate checkInDate,
        @NotNull(message = REQUIRED_MSG) @FutureOrPresent(message = "Future") LocalDate checkOutDate,

        @Positive Integer expectedGuests,

        @NotEmpty(message = REQUIRED_MSG) List<UUID> roomIds,

        @NotNull(message = REQUIRED_MSG) @FutureOrPresent(message = "Future") LocalDate validUntil) {

    /** Shared validation message for required fields. */
    static final String REQUIRED_MSG = "Required";

    /**
     * Compact constructor — defensive copy of the room id list.
     */
    public QuotationRequest {
        roomIds = roomIds == null ? List.of() : List.copyOf(roomIds);
    }

    /**
     * Returns a copy of the room id list to prevent external modification.
     *
     * @return the requested room ids
     */
    @Override
    public List<UUID> roomIds() {
        return List.copyOf(roomIds);
    }

    /**
     * Enforces that either an existing guest or a full prospect identity is
     * provided — mirrors the DB-level {@code chk_quotations_guest_or_prospect}
     * constraint (V10) at the API boundary.
     *
     * @return {@code true} when the request identifies a guest or a prospect
     */
    @AssertTrue(message = "Either guestId or prospectFirstName/prospectLastName/prospectEmail is required")
    public boolean isGuestOrProspectProvided() {
        return guestId != null
                || prospectFirstName != null && !prospectFirstName.isBlank()
                        && prospectLastName != null && !prospectLastName.isBlank()
                        && prospectEmail != null && !prospectEmail.isBlank();
    }

    /**
     * Enforces that {@code checkOutDate} is strictly after {@code checkInDate}
     * and {@code validUntil} is not after {@code checkInDate} — a quotation
     * that is still "valid" after the stay has already started makes no sense.
     *
     * @return {@code true} when the date relationship is valid or a date is null
     *         (individual {@code @NotNull} constraints handle missing dates)
     */
    @AssertTrue(message = "checkOutDate must be after checkInDate and validUntil must not be after checkInDate")
    public boolean isDateRangeValid() {
        if (checkInDate == null || checkOutDate == null) {
            return true;
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            return false;
        }
        return validUntil == null || !validUntil.isAfter(checkInDate);
    }
}
