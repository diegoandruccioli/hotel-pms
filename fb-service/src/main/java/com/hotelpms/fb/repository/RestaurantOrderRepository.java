package com.hotelpms.fb.repository;

import com.hotelpms.fb.domain.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing RestaurantOrder entities.
 */
@Repository
public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    /**
     * Finds all restaurant orders associated with a specific stay.
     *
     * @param stayId the ID of the stay
     * @return a list of associated orders
     */
    List<RestaurantOrder> findByStayId(UUID stayId);
}
