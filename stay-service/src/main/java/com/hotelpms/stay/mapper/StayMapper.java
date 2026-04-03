package com.hotelpms.stay.mapper;

import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for translating Stay entities and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StayMapper {

    /**
     * Converts a Stay entity into a StayResponse DTO.
     *
     * @param stay the stay entity
     * @return the StayResponse DTO
     */
    StayResponse toDto(Stay stay);

    /**
     * Converts a StayRequest DTO to a Stay entity.
     *
     * @param request the stay request
     * @return the stay entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    Stay toEntity(StayRequest request);

    /**
     * Updates an existing Stay entity with data from a StayRequest DTO.
     *
     * @param request the stay request containing new data
     * @param stay    the entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntityFromDto(StayRequest request, @MappingTarget Stay stay);
}
