package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.client.StayClient;
import com.hotelpms.fb.client.dto.StayResponse;
import com.hotelpms.fb.domain.MenuItem;
import com.hotelpms.fb.domain.OrderItem;
import com.hotelpms.fb.domain.OrderStatus;
import com.hotelpms.fb.domain.RestaurantOrder;
import com.hotelpms.fb.dto.OrderItemRequest;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import com.hotelpms.fb.exception.OrderValidationException;
import com.hotelpms.fb.exception.StayNotFoundException;
import com.hotelpms.fb.mapper.RestaurantOrderMapper;
import com.hotelpms.fb.repository.MenuItemRepository;
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
import java.util.ArrayList;
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
    private final MenuItemRepository menuItemRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RestaurantOrderResponse createOrder(final RestaurantOrderRequest request) {
        log.info("Creating order for stay: {}", request.stayId());

        // Verify the stay is valid and active
        try {
            final StayResponse stayResponse = stayClient.getStayById(request.stayId());
            if ("UNKNOWN".equals(stayResponse.status())) {
                throw new StayNotFoundException("STAY_NOT_FOUND");
            }
        } catch (final feign.FeignException e) {
            log.error("Error communicating with Stay Service for ID: {}", request.stayId(), e);
            throw new StayNotFoundException("STAY_NOT_FOUND", e);
        }

        // Build the order shell (no items yet)
        final RestaurantOrder order = orderMapper.toEntity(request);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.BILLED_TO_ROOM);

        // Resolve items with server-side prices from the catalog (T-FB-02 mitigation)
        final List<OrderItem> items = buildItemsFromCatalog(request.items(), order);
        items.forEach(order::addItem);

        // Calculate total amount using only server-side prices
        final BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

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

    /**
     * Resolves each requested item against the server-side menu catalog and
     * constructs {@link OrderItem} instances with authoritative prices.
     *
     * <p>This prevents client-side price tampering: the client supplies only a
     * {@code menuItemId} and {@code quantity}; the unit price is always fetched
     * from the {@code menu_items} table (T-FB-02).
     *
     * @param itemRequests the list of item requests from the client
     * @param order        the parent order (used for bidirectional FK)
     * @return list of fully populated OrderItem entities with server-side prices
     * @throws OrderValidationException if any {@code menuItemId} does not exist
     *                                  in the active catalog
     */
    private List<OrderItem> buildItemsFromCatalog(
            final List<OrderItemRequest> itemRequests,
            final RestaurantOrder order) {

        final List<OrderItem> items = new ArrayList<>();
        for (final OrderItemRequest req : itemRequests) {
            final MenuItem menuItem = menuItemRepository.findById(req.menuItemId())
                    .orElseThrow(() -> new OrderValidationException(
                            "MENU_ITEM_NOT_FOUND: " + req.menuItemId()));

            final OrderItem item = OrderItem.builder()
                    .restaurantOrder(order)
                    .itemName(menuItem.getName())
                    .unitPrice(menuItem.getPrice())   // server-side price — never from client
                    .quantity(req.quantity())
                    .build();
            items.add(item);
        }
        return items;
    }
}
