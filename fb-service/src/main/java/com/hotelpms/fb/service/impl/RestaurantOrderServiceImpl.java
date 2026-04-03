package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.client.StayClient;
import com.hotelpms.fb.client.dto.StayResponse;
import com.hotelpms.fb.domain.OrderItem;
import com.hotelpms.fb.domain.OrderStatus;
import com.hotelpms.fb.domain.RestaurantOrder;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import com.hotelpms.fb.exception.StayNotFoundException;
import com.hotelpms.fb.mapper.RestaurantOrderMapper;
import com.hotelpms.fb.repository.RestaurantOrderRepository;
import com.hotelpms.fb.service.RestaurantOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the RestaurantOrderService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantOrderServiceImpl implements RestaurantOrderService {

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantOrderMapper orderMapper;
    private final StayClient stayClient;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RestaurantOrderResponse createOrder(final RestaurantOrderRequest request) {
        log.info("Creating order for stay: {}", request.stayId());

        // Verify the stay is valid and active
        final StayResponse stayResponse;
        try {
            stayResponse = stayClient.getStayById(request.stayId());
            if ("UNKNOWN".equals(stayResponse.status())) {
                throw new StayNotFoundException("STAY_NOT_FOUND");
            }
        } catch (final feign.FeignException e) {
            log.error("Error communicating with Stay Service for ID: {}", request.stayId(), e);
            throw new StayNotFoundException("STAY_NOT_FOUND", e);
        }

        // Map DTO to entity
        final RestaurantOrder order = orderMapper.toEntity(request);
        order.setOrderDate(LocalDateTime.now());

        // At this moment we define a default status since we are billing to room
        order.setStatus(OrderStatus.BILLED_TO_ROOM);

        // Calculate total amount
        final BigDecimal totalAmount = order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        // Ensure bidirectional relationship is established
        for (final OrderItem item : order.getItems()) {
            item.setRestaurantOrder(order);
        }

        // Save order
        final RestaurantOrder savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {}", savedOrder.getId());

        return orderMapper.toResponse(savedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<RestaurantOrderResponse> getOrdersByStayId(final UUID stayId) {
        log.info("Fetching orders for stay: {}", stayId);
        final List<RestaurantOrder> orders = orderRepository.findByStayId(stayId);
        return orders.stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RestaurantOrderResponse> getAllOrders(final Pageable pageable) {
        log.info("Fetching paginated restaurant orders, page: {}", pageable.getPageNumber());
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }
}
