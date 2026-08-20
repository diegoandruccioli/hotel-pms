package com.hotelpms.frontdesk.citytax.mapper;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Mapper for {@link CityTaxRate}. No {@code toEntity} — the service builds the
 * entity itself, since {@code hotelId}/{@code comuneCodice} come from context
 * (security context, the hotel's own settings) rather than the request body.
 */
@FunctionalInterface
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CityTaxRateMapper {

    /**
     * Converts entity to response.
     *
     * @param entity the entity
     * @return the response
     */
    CityTaxRateResponse toResponse(CityTaxRate entity);
}
