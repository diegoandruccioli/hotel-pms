package com.hotelpms.frontdesk.citytax.mapper;

import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Mapper for {@link HotelCategoryHistory}. No {@code toEntity} — the service
 * builds the entity itself, since {@code hotelId} comes from the security
 * context rather than the request body.
 */
@FunctionalInterface
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HotelCategoryHistoryMapper {

    /**
     * Converts entity to response.
     *
     * @param entity the entity
     * @return the response
     */
    HotelCategoryHistoryResponse toResponse(HotelCategoryHistory entity);
}
