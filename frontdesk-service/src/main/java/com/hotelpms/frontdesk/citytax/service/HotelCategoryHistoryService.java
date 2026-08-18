package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryRequest;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Management operations for a hotel's classification/category history.
 * Append-only — see {@code recordCategory} javadoc — there is no update/delete.
 */
public interface HotelCategoryHistoryService {

    /**
     * Lists the caller's hotel's full category history, current entry first.
     *
     * @param hotelId the authenticated hotel UUID
     * @return the history, newest {@code validFrom} first
     */
    List<HotelCategoryHistoryResponse> listHistory(UUID hotelId);

    /**
     * Records a new category for the caller's hotel. If an open-ended entry
     * already exists, it is closed (its {@code validTo} set to this entry's
     * {@code validFrom}) before the new one is inserted.
     *
     * @param hotelId the authenticated hotel UUID
     * @param request the category details
     * @return the created entry
     */
    HotelCategoryHistoryResponse recordCategory(UUID hotelId, HotelCategoryHistoryRequest request);
}
