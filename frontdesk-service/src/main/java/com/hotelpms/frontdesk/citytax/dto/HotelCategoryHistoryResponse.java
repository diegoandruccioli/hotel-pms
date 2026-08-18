package com.hotelpms.frontdesk.citytax.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for a {@code HotelCategoryHistory} entry.
 *
 * @param id        the entry UUID
 * @param category  classification label
 * @param validFrom the date this category took/takes effect
 * @param validTo   the date this category stopped applying, or {@code null}
 *                  if it is the hotel's current category
 */
public record HotelCategoryHistoryResponse(
        UUID id,
        String category,
        LocalDate validFrom,
        LocalDate validTo) {
}
