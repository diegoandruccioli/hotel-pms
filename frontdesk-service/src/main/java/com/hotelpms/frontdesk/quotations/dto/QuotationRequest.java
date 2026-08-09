package com.hotelpms.frontdesk.quotations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a {@code Quotation}. No price field on any option
 * — every total is always resolved server-side via {@code RatePricingService},
 * same principle as {@code ReservationLineItemRequest}.
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
 * @param options           the 1-5 alternative room combinations to offer
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

        @Valid @NotEmpty(message = REQUIRED_MSG) @Size(max = MAX_OPTIONS) List<QuotationOptionRequest> options,

        @NotNull(message = REQUIRED_MSG) @FutureOrPresent(message = "Future") LocalDate validUntil) {

    /** Shared validation message for required fields. */
    static final String REQUIRED_MSG = "Required";

    /** A quotation may offer at most this many alternatives. */
    static final int MAX_OPTIONS = 5;

    /**
     * Compact constructor — defensive copy of the options list.
     */
    public QuotationRequest {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /**
     * Returns a copy of the options list to prevent external modification.
     *
     * @return the requested options
     */
    @Override
    public List<QuotationOptionRequest> options() {
        return List.copyOf(options);
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
