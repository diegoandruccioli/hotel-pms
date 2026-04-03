package com.hotelpms.inventory.mapper;

import com.hotelpms.inventory.domain.RoomType;
import com.hotelpms.inventory.dto.RoomTypeRequest;
import com.hotelpms.inventory.dto.RoomTypeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * Mapper for RoomType.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RoomTypeMapper {

    /**
     * Converts entity to response.
     * 
     * @param entity the entity
     * @return the response
     */
    RoomTypeResponse toResponse(RoomType entity);

    /**
     * Converts request to entity.
     * 
     * @param request the request
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RoomType toEntity(RoomTypeRequest request);
}
