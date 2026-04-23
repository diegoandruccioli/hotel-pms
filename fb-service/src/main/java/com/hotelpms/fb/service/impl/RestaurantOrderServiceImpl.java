package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.client.BillingClient;
import com.hotelpms.fb.client.StayClient;
import com.hotelpms.fb.client.dto.ChargeRequest;
import com.hotelpms.fb.client.dto.ChargeResponse;
import com.hotelpms.fb.client.dto.StayResponse;
import com.hotelpms.fb.domain.MenuItem;
import com.hotelpms.fb.domain.OrderItem;
import com.hotelpms.fb.domain.OrderStatus;
import com.hotelpms.fb.domain.RestaurantOrder;
import com.hotelpms.fb.dto.OrderItemRequest;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import com.hotelpms.fb.exception.OrderNotFoundException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the RestaurantOrderService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantOrderServiceImpl implements RestaurantOrderService {

    private static final String CHARGE_TYPE_FB_ORDER = "FB_ORDER";
    private static final Set<OrderStatus> CONFIRMABLE_STATUSES = Set.of(OrderStatus.PENDING, OrderStatus.PREPARED);

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantOrderMapper orderMapper;
    private final StayClient stayClient;
    private final MenuItemRepository menuItemRepository;
    private final BillingClient billingClient;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RestaurantOrderResponse createOrder(final RestaurantOrderRequest request) {
        final UUID hotelId = resolveHotelId();
        log.info("Creating order for stay: {} hotel: {}", request.stayId(), hotelId);

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
        order.setHotelId(hotelId);   // T-FB-01: set server-side, never from client
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);

        // Resolve items with server-side prices from the catalog (T-FB-02 mitigation)
        final List<OrderItem> items = buildItemsFromCatalog(request.items(), order);
        items.forEach(order::addItem);

        // Calculate total amount using only server-side prices
        final BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        final RestaurantOrder savedOrder = orderRepository.save(order);
        log.info("[FB] ORDER_CREATED | orderId={} | stayId={} | hotelId={} | total={}",
                savedOrder.getId(), savedOrder.getStayId(), hotelId, savedOrder.getTotalAmount());

        return orderMapper.toResponse(savedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<RestaurantOrderResponse> getOrdersByStayId(final UUID stayId) {
        final UUID hotelId = resolveHotelId();
        log.info("Fetching orders for stay: {} hotel: {}", stayId, hotelId);
        // T-FB-01: hotel-scoped — returns empty list for stayIds of other hotels (IDOR-safe)
        final List<RestaurantOrder> orders = orderRepository.findByStayIdAndHotelId(stayId, hotelId);
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
        final UUID hotelId = resolveHotelId();
        log.info("Fetching paginated restaurant orders, page: {}, hotel: {}",
                pageable.getPageNumber(), hotelId);
        // T-FB-01: hotel-scoped — never returns orders from other hotels
        return orderRepository.findAllByHotelId(hotelId, pageable).map(orderMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RestaurantOrderResponse confirmOrder(final UUID orderId) {
        final UUID hotelId = resolveHotelId();
        log.info("Confirming order: {} hotel: {}", orderId, hotelId);

        final RestaurantOrder order = orderRepository.findByIdAndHotelId(
                Objects.requireNonNull(orderId), hotelId)
                .orElseThrow(() -> new OrderNotFoundException("ORDER_NOT_FOUND"));

        if (!CONFIRMABLE_STATUSES.contains(order.getStatus())) {
            log.warn("[FB] CONFIRM_FAILED | orderId={} | reason=INVALID_ORDER_STATUS | currentStatus={}",
                    orderId, order.getStatus());
            throw new OrderValidationException("INVALID_ORDER_STATUS");
        }

        order.setStatus(OrderStatus.BILLED_TO_ROOM);
        final RestaurantOrder savedOrder = orderRepository.save(order);

        final ChargeRequest chargeReq = new ChargeRequest(
                CHARGE_TYPE_FB_ORDER,
                "F&B " + orderId,
                savedOrder.getTotalAmount(),
                orderId);
        final ChargeResponse chargeResp = billingClient.addCharge(savedOrder.getStayId(), chargeReq);
        if (chargeResp == null) {
            log.warn("[FB] CHARGE_FAILED | orderId={} | stayId={} | reason=BILLING_SERVICE_UNAVAILABLE",
                    orderId, savedOrder.getStayId());
        } else {
            log.info("[FB] CHARGE_ADDED | orderId={} | chargeId={}", orderId, chargeResp.id());
        }

        return orderMapper.toResponse(savedOrder);
    }

    /**
     * Extracts the hotel identifier from the current security context.
     *
     * <p>The hotel ID is stored in {@link org.springframework.security.authentication
     * .UsernamePasswordAuthenticationToken#getDetails()} by {@code InternalAuthFilter},
     * which reads it from the {@code X-Auth-Hotel} header injected by the API Gateway
     * after JWT validation. This prevents any client from supplying a forged hotel scope.
     *
     * @return the hotel UUID for the authenticated request
     * @throws IllegalStateException if the security context does not contain a valid hotel ID
     */
    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final Object details = auth.getDetails();
        if (!(details instanceof String hotelIdStr) || !StringUtils.hasText(hotelIdStr)) {
            throw new IllegalStateException("X-Auth-Hotel_MISSING");
        }
        return UUID.fromString(hotelIdStr);
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
            final MenuItem menuItem = menuItemRepository.findById(Objects.requireNonNull(req.menuItemId()))
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
