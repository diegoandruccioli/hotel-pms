/**
 * Imposta di soggiorno (Italian municipal tourist tax, art. 4 D.Lgs. 23/2011):
 * per-hotel versioned rate rules ({@code CityTaxRate}), the hotel's own
 * classification history ({@code HotelCategoryHistory}, since several comuni
 * tier the rate by category/stelle), and the deterministic calculation +
 * immutable per-stay audit record ({@code CityTaxCalculator},
 * {@code CityTaxAssessment}). Consumed by
 * {@code com.hotelpms.frontdesk.stays.service.impl.StayBillingCoordinator} at
 * check-in, which posts the resulting amount to billing-service as a
 * {@code CITY_TAX} charge — the same path already used for {@code ROOM_NIGHT}.
 */
package com.hotelpms.frontdesk.citytax;
