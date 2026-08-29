package com.hotelpms.frontdesk.citytax.dto;

import com.hotelpms.frontdesk.stays.domain.CityTaxApplicability;

/**
 * Response DTO for a hotel's declared tourist-tax applicability.
 *
 * @param applicability the declared applicability
 */
public record CityTaxApplicabilityResponse(CityTaxApplicability applicability) {
}
