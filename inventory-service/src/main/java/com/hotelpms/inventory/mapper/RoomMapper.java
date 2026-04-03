package com.hotelpms.inventory.mapper;

import com.hotelpms.inventory.domain.Room;
import com.hotelpms.inventory.dto.RoomRequest;
import com.hotelpms.inventory.dto.RoomResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * Mapper for Room.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = RoomTypeMapper.class)
public interface RoomMapper {

    /**
     * Converts entity to response.
     *
     * @param entity the entity
     * @return the response
     */
    RoomResponse toResponse(Room entity);

    /**
     * Converts request to entity.
     *
     * @param request the request
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roomType", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Room toEntity(RoomRequest request);
}
