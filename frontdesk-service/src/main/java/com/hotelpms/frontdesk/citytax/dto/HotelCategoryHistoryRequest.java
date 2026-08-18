package com.hotelpms.frontdesk.citytax.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for recording a hotel's classification/category. Creating an
 * entry always adds a new row and closes the hotel's current open-ended entry
 * (if any) by setting its {@code validTo} to this entry's {@code validFrom} —
 * there is no update/delete endpoint (append-only, see
 * {@link com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory}).
 *
 * @param category  classification label (e.g. comune-specific stelle/category naming)
 * @param validFrom the date this category takes effect
 */
public record HotelCategoryHistoryRequest(
        @NotBlank(message = "Required") @Size(max = MAX_CATEGORY_LENGTH) String category,

        @NotNull(message = "Required") LocalDate validFrom) {

    private static final int MAX_CATEGORY_LENGTH = 20;
}
