package com.hotelpms.frontdesk.citytax.dto;

import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;

/**
 * Whether the caller's hotel is ready to assess tourist tax for a check-in
 * today. Used by the check-in form to show a pre-flight warning before the
 * operator submits, rather than only after the stay is created.
 *
 * @param configured {@code true} if a check-in today would actually be
 *                   assessed (including the deliberate {@code NOT_APPLICABLE}
 *                   case); {@code false} if it would silently assess nothing
 * @param reason     why it isn't configured, or {@code null} when {@code configured}
 */
public record CityTaxConfigurationStatusResponse(boolean configured, CityTaxUnassessedReason reason) {
}
