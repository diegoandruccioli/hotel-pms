package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.client.StayClient;
import com.hotelpms.fb.client.dto.StayResponse;
import com.hotelpms.fb.domain.MenuItem;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderServiceImplTest {

    private static final String STATUS_CHECKED_IN = "CHECKED_IN";

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private RestaurantOrderMapper orderMapper;

    @Mock
    private StayClient stayClient;

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private RestaurantOrderServiceImpl orderService;

    private UUID stayId;
    private UUID menuItemId;
    private RestaurantOrderRequest request;
    private RestaurantOrder order;
    private RestaurantOrderResponse response;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        menuItemId = UUID.randomUUID();

        final OrderItemRequest itemRequest = new OrderItemRequest(menuItemId, 2);
        request = RestaurantOrderRequest.builder()
                .stayId(stayId)
                .items(List.of(itemRequest))
                .build();

        // MenuItem with server-side price
        menuItem = MenuItem.builder()
                .id(menuItemId)
                .name("Pizza")
                .price(new BigDecimal("15.00"))
                .build();

        // Order returned by mapper has a mutable items list so the service can call addItem()
        order = RestaurantOrder.builder()
                .stayId(stayId)
                .build();

        response = RestaurantOrderResponse.builder()
                .id(UUID.randomUUID())
                .stayId(stayId)
                .totalAmount(new BigDecimal("30.00"))
                .status(OrderStatus.BILLED_TO_ROOM)
                .build();
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // Arrange
        when(stayClient.getStayById(stayId)).thenReturn(new StayResponse(stayId, STATUS_CHECKED_IN));
        when(orderMapper.toEntity(request)).thenReturn(order);
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem));
        when(orderRepository.save(any(RestaurantOrder.class))).thenReturn(order);
        when(orderMapper.toResponse(Objects.requireNonNull(order))).thenReturn(response);

        // Act
        final RestaurantOrderResponse result = orderService.createOrder(request);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("30.00"), result.totalAmount());
        assertEquals(OrderStatus.BILLED_TO_ROOM, result.status());

        verify(stayClient).getStayById(stayId);
        verify(menuItemRepository).findById(menuItemId);
        verify(orderRepository).save(any(RestaurantOrder.class));
    }

    @Test
    void shouldUsePriceFromCatalogNotFromClient() {
        // Arrange: server-side price is 15.00; client has no way to override it
        when(stayClient.getStayById(stayId)).thenReturn(new StayResponse(stayId, STATUS_CHECKED_IN));
        when(orderMapper.toEntity(request)).thenReturn(order);

        final MenuItem expensiveItem = MenuItem.builder()
                .id(menuItemId)
                .name("Bistecca ai Ferri")
                .price(new BigDecimal("22.00"))
                .build();
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(expensiveItem));
        when(orderRepository.save(any(RestaurantOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(RestaurantOrder.class))).thenReturn(response);

        // Act
        orderService.createOrder(request);

        // Assert: verify the item was built using catalog price (22.00), not any client value
        verify(menuItemRepository).findById(menuItemId);
        // The order passed to save must contain items with server-side price 22.00
        verify(orderRepository).save(any(RestaurantOrder.class));
    }

    @Test
    void shouldThrowExceptionWhenMenuItemNotFound() {
        // Arrange: menu item UUID does not exist in the catalog
        when(stayClient.getStayById(stayId)).thenReturn(new StayResponse(stayId, STATUS_CHECKED_IN));
        when(orderMapper.toEntity(request)).thenReturn(order);
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.empty());

        // Act & Assert
        final OrderValidationException exception = assertThrows(OrderValidationException.class,
                () -> orderService.createOrder(request));

        assertNotNull(exception.getMessage());
        verify(menuItemRepository).findById(menuItemId);
        verify(orderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenStayUnknown() {
        // Arrange
        when(stayClient.getStayById(stayId)).thenReturn(new StayResponse(stayId, "UNKNOWN"));

        // Act & Assert
        final StayNotFoundException exception = assertThrows(StayNotFoundException.class,
                () -> orderService.createOrder(request));

        assertNotNull(exception.getMessage());
        verify(stayClient).getStayById(stayId);
        verifyNoInteractions(orderMapper, orderRepository, menuItemRepository);
    }

    @Test
    void shouldThrowExceptionWhenStayClientFails() {
        // Arrange
        when(stayClient.getStayById(stayId)).thenThrow(org.mockito.Mockito.mock(feign.FeignException.class));

        // Act & Assert
        final StayNotFoundException exception = assertThrows(StayNotFoundException.class,
                () -> orderService.createOrder(request));

        assertNotNull(exception.getMessage());
        verify(stayClient).getStayById(stayId);
        verifyNoInteractions(orderMapper, orderRepository, menuItemRepository);
    }

    @Test
    void testGetOrdersByStayId() {
        // Arrange
        when(orderRepository.findByStayId(stayId)).thenReturn(List.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        // Act
        final List<RestaurantOrderResponse> results = orderService.getOrdersByStayId(stayId);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(response, results.get(0));

        verify(orderRepository).findByStayId(stayId);
    }
}
