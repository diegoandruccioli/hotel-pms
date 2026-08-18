package com.hotelpms.frontdesk.citytax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for a {@code CityTaxRate}.
 *
 * @param id               the rule UUID
 * @param comuneCodice     the comune this rule applies to (Polizia di Stato code)
 * @param category         classification label this rule applies to
 * @param amountPerNight   the tax amount per taxable night
 * @param maxTaxableNights cap on taxable nights, or {@code null} if uncapped
 * @param exemptUnderAge   age exemption threshold, or {@code null} if none
 * @param validFrom        the date this rule took/takes effect
 * @param validTo          the date this rule stopped applying, or {@code null}
 *                         if it is the current rule
 * @param note             free-text reference to the delibera/regolamento, if any
 */
public record CityTaxRateResponse(
        UUID id,
        String comuneCodice,
        String category,
        BigDecimal amountPerNight,
        Integer maxTaxableNights,
        Integer exemptUnderAge,
        LocalDate validFrom,
        LocalDate validTo,
        String note) {
}
