package com.hotelpms.fb.repository;

import com.hotelpms.fb.domain.RestaurantOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing RestaurantOrder entities.
 *
 * <p>All queries are scoped to {@code hotelId} to enforce multi-tenant isolation (T-FB-01).
 */
@Repository
public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    /**
     * Finds all active orders for a specific stay within the caller's hotel.
     * Returns an empty list when {@code stayId} belongs to a different hotel
     * (IDOR-safe: no cross-hotel leakage).
     *
     * @param stayId  the ID of the stay
     * @param hotelId the hotel scope extracted from the authenticated request
     * @return hotel-scoped list of orders for the given stay
     */
    List<RestaurantOrder> findByStayIdAndHotelId(UUID stayId, UUID hotelId);

    /**
     * Returns a paginated list of all active orders for the given hotel.
     *
     * @param hotelId  the hotel scope extracted from the authenticated request
     * @param pageable pagination and sorting parameters
     * @return hotel-scoped page of orders
     */
    Page<RestaurantOrder> findAllByHotelId(UUID hotelId, Pageable pageable);
}
