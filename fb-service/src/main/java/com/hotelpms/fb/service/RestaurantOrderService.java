package com.hotelpms.fb.service;

import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing restaurant orders.
 */
public interface RestaurantOrderService {

    /**
     * Creates a new restaurant order.
     *
     * @param request the order creation request
     * @return the created order response
     */
    RestaurantOrderResponse createOrder(RestaurantOrderRequest request);

    /**
     * Retrieves all orders for a specific stay.
     *
     * @param stayId the stay ID
     * @return a list of order responses
     */
    List<RestaurantOrderResponse> getOrdersByStayId(UUID stayId);

    /**
     * Retrieves a paginated list of all restaurant orders.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of order responses
     */
    Page<RestaurantOrderResponse> getAllOrders(Pageable pageable);

    /**
     * Confirms a restaurant order: transitions it to BILLED_TO_ROOM and charges
     * the amount to the guest's open billing invoice via billing-service.
     *
     * @param orderId the order UUID to confirm
     * @return the updated order response
     */
    RestaurantOrderResponse confirmOrder(UUID orderId);
}
