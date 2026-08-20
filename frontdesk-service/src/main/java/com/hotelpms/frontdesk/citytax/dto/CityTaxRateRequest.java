package com.hotelpms.frontdesk.citytax.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a {@code CityTaxRate}. The comune is not part of
 * this body — it is resolved server-side from the caller's own
 * {@code HotelSettings.comuneCodice}, so a hotel can never create a rule for
 * a comune it isn't itself configured in.
 *
 * <p>Creating a rule always adds a new row; there is no update/delete
 * endpoint (append-only, see {@link com.hotelpms.frontdesk.citytax.domain.CityTaxRate}).
 * A create implicitly closes the hotel's current open-ended rule (if any)
 * for the same category by setting its {@code validTo} to this rule's
 * {@code validFrom}.
 *
 * @param category         classification label this rule applies to (e.g.
 *                         the comune's own stelle/category naming)
 * @param amountPerNight   the tax amount per taxable night
 * @param maxTaxableNights optional cap on taxable nights; {@code null} = uncapped
 * @param exemptUnderAge   optional age exemption threshold (guests strictly
 *                         under this age at check-in are exempt); {@code null}
 *                         = no age exemption
 * @param validFrom        the date this rule takes effect
 * @param note             optional free-text reference to the delibera/regolamento
 */
public record CityTaxRateRequest(
        @NotBlank(message = "Required") @Size(max = MAX_CATEGORY_LENGTH) String category,

        @NotNull(message = "Required") @PositiveOrZero BigDecimal amountPerNight,

        @Positive Integer maxTaxableNights,

        @Min(0) @Max(MAX_AGE) Integer exemptUnderAge,

        @NotNull(message = "Required") LocalDate validFrom,

        @Size(max = MAX_NOTE_LENGTH) String note) {

    private static final int MAX_CATEGORY_LENGTH = 20;
    private static final int MAX_AGE = 120;
    private static final int MAX_NOTE_LENGTH = 200;
}
