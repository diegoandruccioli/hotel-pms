package com.hotelpms.frontdesk.pricing.mapper;

import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonRequest;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * Mapper for RateSeason.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RateSeasonMapper {

    /**
     * Converts entity to response.
     *
     * @param entity the entity
     * @return the response
     */
    RateSeasonResponse toResponse(RateSeason entity);

    /**
     * Converts request to a new entity. {@code roomTypeId}/{@code hotelId} are
     * set by the service from the path/security context, never from the body.
     *
     * @param request the request
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "roomTypeId", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RateSeason toEntity(RateSeasonRequest request);

    /**
     * Applies request fields onto an existing entity for an update, leaving
     * identity/tenant/audit fields untouched.
     *
     * @param request the request
     * @param entity  the entity to update in place
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "roomTypeId", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(RateSeasonRequest request, @MappingTarget RateSeason entity);
}
