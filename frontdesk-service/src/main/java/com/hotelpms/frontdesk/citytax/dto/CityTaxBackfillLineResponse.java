package com.hotelpms.frontdesk.citytax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One stay's tourist-tax correction, computed with the rate/category that was
 * actually in effect on the stay's own check-in date — never today's rate.
 *
 * @param stayId       the stay UUID
 * @param checkInDate  the stay's check-in date
 * @param amount       the tax amount that should have been assessed; {@code
 *                     0} if configuration is still missing for that date
 * @param charged      {@code true} once this line's {@code CITY_TAX} charge
 *                     was actually posted (only ever true on {@code /confirm},
 *                     never on {@code /preview})
 * @param skipReason   why this line wasn't (or won't be) charged — {@code
 *                     INVOICE_NOT_OPEN} (fattura già chiusa: gestita a parte
 *                     con nota di variazione, mai un documento fiscale
 *                     riaperto), {@code STILL_UNCONFIGURED} (configuration
 *                     still doesn't cover this date), or {@code null} when charged
 */
public record CityTaxBackfillLineResponse(
        UUID stayId,
        LocalDate checkInDate,
        BigDecimal amount,
        boolean charged,
        String skipReason) {
}
