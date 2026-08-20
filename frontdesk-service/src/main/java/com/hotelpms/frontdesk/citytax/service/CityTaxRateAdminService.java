package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.dto.CityTaxRateRequest;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateResponse;

import java.util.List;
import java.util.UUID;

/**
 * Management operations for {@code CityTaxRate} rows, scoped per hotel.
 * Append-only — see {@code createRule} javadoc — there is no update/delete.
 */
public interface CityTaxRateAdminService {

    /**
     * Lists every rate rule for the caller's hotel, current rule first.
     *
     * @param hotelId the authenticated hotel UUID
     * @return the rules, newest {@code validFrom} first
     */
    List<CityTaxRateResponse> listRules(UUID hotelId);

    /**
     * Creates a rate rule for the caller's hotel. The comune is resolved
     * server-side from the hotel's own {@code HotelSettings.comuneCodice} —
     * never accepted from the request. If an open-ended rule already exists
     * for the same (comune, category), it is closed (its {@code validTo} set
     * to this rule's {@code validFrom}) before the new one is inserted.
     *
     * @param hotelId the authenticated hotel UUID
     * @param request the rule details
     * @return the created rule
     */
    CityTaxRateResponse createRule(UUID hotelId, CityTaxRateRequest request);
}
