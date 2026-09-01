package com.hotelpms.frontdesk.citytax.dto;

import com.hotelpms.frontdesk.stays.domain.CityTaxApplicability;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for declaring whether a hotel's comune levies a tourist tax at all.
 *
 * @param applicability the declared applicability
 */
public record CityTaxApplicabilityRequest(@NotNull(message = "Required") CityTaxApplicability applicability) {
}
