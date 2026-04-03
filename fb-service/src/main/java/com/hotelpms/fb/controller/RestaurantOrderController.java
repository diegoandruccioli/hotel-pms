package com.hotelpms.fb.controller;

import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import com.hotelpms.fb.service.RestaurantOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing restaurant orders.
 */
@RestController
@RequestMapping("/api/v1/fb/orders")
@RequiredArgsConstructor
@Slf4j
public class RestaurantOrderController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final RestaurantOrderService orderService;

    /**
     * Creates a new restaurant order.
     *
     * @param request the order creation request
     * @return the created order
     */
    @PostMapping
    public ResponseEntity<RestaurantOrderResponse> createOrder(
            @NonNull @Valid @RequestBody final RestaurantOrderRequest request) {
        log.info("REST request to create restaurant order for stay: {}", request.stayId());
        final RestaurantOrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves all orders for a specific stay.
     *
     * @param stayId the stay ID
     * @return list of orders for the stay
     */
    @GetMapping("/stay/{stayId}")
    public ResponseEntity<List<RestaurantOrderResponse>> getOrdersByStayId(@NonNull @PathVariable final UUID stayId) {
        log.info("REST request to get orders for stay: {}", stayId);
        final List<RestaurantOrderResponse> responses = orderService.getOrdersByStayId(stayId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a paginated list of all restaurant orders.
     * Supports standard Spring Data pagination query parameters:
     * {@code ?page=0&size=20&sort=orderDate,desc}
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of order responses
     */
    @GetMapping
    public ResponseEntity<Page<RestaurantOrderResponse>> getAllOrders(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "orderDate",
                    direction = Sort.Direction.DESC) final Pageable pageable) {
        log.info("REST request to get all paginated restaurant orders");
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }
}
