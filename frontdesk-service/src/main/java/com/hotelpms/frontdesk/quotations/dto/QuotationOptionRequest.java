package com.hotelpms.frontdesk.quotations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * One alternative offered within a {@code QuotationRequest} — a label and
 * the rooms it bundles. Price is never accepted from the client: it is
 * resolved server-side the same way a single-option quotation's price is.
 *
 * @param label   the option's display label (e.g. "Standard Double")
 * @param roomIds the rooms this option bundles
 */
public record QuotationOptionRequest(
        @NotBlank(message = "Required") @Size(max = 100) String label,

        @NotEmpty(message = "Required") @Size(max = MAX_ROOM_IDS, message = "Too many rooms") List<UUID> roomIds) {

    /**
     * Upper bound on rooms bundled in a single option (Finding #18,
     * security-report.md — LOW) — same cap as {@code ReservationRequest.lineItems},
     * since an option is a candidate room block of the same realistic scale.
     */
    static final int MAX_ROOM_IDS = 50;

    /**
     * Compact constructor — defensive copy of the room id list.
     */
    public QuotationOptionRequest {
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
}
