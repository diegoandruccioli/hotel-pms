package com.hotelpms.frontdesk.pricing.service;

import com.hotelpms.frontdesk.pricing.dto.RateSeasonRequest;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;

import java.util.List;
import java.util.UUID;

/**
 * Management (CRUD) operations for {@code RateSeason} rows, scoped per hotel
 * and per room type. Distinct from {@link RatePricingService}, which only
 * *resolves* a price and has no create/update/delete surface — this interface
 * is what the "manage seasons" screen and API talk to.
 */
public interface RateSeasonAdminService {

    /**
     * Lists every active season for a room type, oldest first.
     *
     * @param roomTypeId the room type UUID
     * @param hotelId    the authenticated hotel UUID
     * @return the seasons in start-date order
     */
    List<RateSeasonResponse> listSeasons(UUID roomTypeId, UUID hotelId);

    /**
     * Creates a season for a room type, scoped to the caller's hotel.
     *
     * @param roomTypeId the room type UUID
     * @param hotelId    the authenticated hotel UUID
     * @param request    the season details
     * @return the created season
     */
    RateSeasonResponse createSeason(UUID roomTypeId, UUID hotelId, RateSeasonRequest request);

    /**
     * Updates a season, scoped to the caller's hotel.
     *
     * @param id      the season UUID
     * @param hotelId the authenticated hotel UUID
     * @param request the updated season details
     * @return the updated season
     */
    RateSeasonResponse updateSeason(UUID id, UUID hotelId, RateSeasonRequest request);

    /**
     * Soft-deletes a season, scoped to the caller's hotel.
     *
     * @param id      the season UUID
     * @param hotelId the authenticated hotel UUID
     */
    void deleteSeason(UUID id, UUID hotelId);
}
