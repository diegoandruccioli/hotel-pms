package com.hotelpms.fb.mapper;

import com.hotelpms.fb.domain.OrderItem;
import com.hotelpms.fb.domain.RestaurantOrder;
import com.hotelpms.fb.dto.OrderItemResponse;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for converting between RestaurantOrder entities and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RestaurantOrderMapper {

    /**
     * Converts a RestaurantOrder request DTO to an entity.
     *
     * <p>Items are intentionally excluded from this mapping and must be
     * populated by the service layer after server-side price lookup.
     *
     * @param request the request DTO
     * @return the entity (without items populated)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    RestaurantOrder toEntity(RestaurantOrderRequest request);

    /**
     * Converts a RestaurantOrder entity to a response DTO.
     *
     * @param entity the entity
     * @return the response DTO
     */
    RestaurantOrderResponse toResponse(RestaurantOrder entity);

    /**
     * Converts an OrderItem entity to a response DTO.
     *
     * @param entity the entity
     * @return the response DTO
     */
    OrderItemResponse toResponse(OrderItem entity);
}
