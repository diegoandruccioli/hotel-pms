package com.hotelpms.fb.mapper;

import com.hotelpms.fb.domain.OrderItem;
import com.hotelpms.fb.domain.RestaurantOrder;
import com.hotelpms.fb.dto.OrderItemRequest;
import com.hotelpms.fb.dto.OrderItemResponse;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for converting between RestaurantOrder entities and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RestaurantOrderMapper {

    /**
     * Converts a RestaurantOrder request DTO to an entity.
     *
     * @param request the request DTO
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    RestaurantOrder toEntity(RestaurantOrderRequest request);

    /**
     * Converts an OrderItem request DTO to an entity.
     *
     * @param request the request DTO
     * @return the entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "restaurantOrder", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    OrderItem toEntity(OrderItemRequest request);

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

    /**
     * Links order items to their parent order after mapping.
     * Required to prevent infinite loops and ensure JPA consistency.
     *
     * @param order the mapped RestaurantOrder
     */
    @AfterMapping
    default void linkOrderItems(@MappingTarget final RestaurantOrder order) {
        if (order.getItems() != null) {
            order.getItems().forEach(item -> item.setRestaurantOrder(order));
        }
    }
}
