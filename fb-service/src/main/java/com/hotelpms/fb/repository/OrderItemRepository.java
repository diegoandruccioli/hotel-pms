package com.hotelpms.fb.repository;

import com.hotelpms.fb.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing OrderItem entities.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Finds all order items associated with a specific restaurant order.
     *
     * @param restaurantOrderId the ID of the restaurant order
     * @return a list of associated order items
     */
    List<OrderItem> findByRestaurantOrderId(UUID restaurantOrderId);
}
