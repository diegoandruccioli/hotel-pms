package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.client.StayClient;
import com.hotelpms.fb.client.dto.StayResponse;
import com.hotelpms.fb.domain.OrderItem;
import com.hotelpms.fb.domain.OrderStatus;
import com.hotelpms.fb.domain.RestaurantOrder;
import com.hotelpms.fb.dto.OrderItemRequest;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import com.hotelpms.fb.exception.StayNotFoundException;
import com.hotelpms.fb.mapper.RestaurantOrderMapper;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderServiceImplTest {

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private RestaurantOrderMapper orderMapper;

    @Mock
    private StayClient stayClient;

    @InjectMocks
    private RestaurantOrderServiceImpl orderService;

    private UUID stayId;
    private RestaurantOrderRequest request;
    private RestaurantOrder order;
    private RestaurantOrderResponse response;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();

        final OrderItemRequest itemRequest = new OrderItemRequest("Pizza", 2, new BigDecimal("15.00"));
        request = RestaurantOrderRequest.builder()
                .stayId(stayId)
                .items(List.of(itemRequest))
                .build();

        final OrderItem item = OrderItem.builder()
                .itemName("Pizza")
                .quantity(2)
                .unitPrice(new BigDecimal("15.00"))
                .build();

        order = RestaurantOrder.builder()
                .stayId(stayId)
                .items(List.of(item))
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
        when(stayClient.getStayById(stayId)).thenReturn(new StayResponse(stayId, "CHECKED_IN"));
        when(orderMapper.toEntity(request)).thenReturn(order);
        when(orderRepository.save(Objects.requireNonNull(order))).thenReturn(order);
        when(orderMapper.toResponse(Objects.requireNonNull(order))).thenReturn(response);

        // Act
        final RestaurantOrderResponse result = orderService.createOrder(request);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("30.00"), result.totalAmount());
        assertEquals(OrderStatus.BILLED_TO_ROOM, result.status());

        verify(stayClient).getStayById(stayId);
        verify(orderRepository).save(Objects.requireNonNull(order));
    }

    @Test
    void shouldThrowExceptionWhenStayUnknown() {
        // Arrange
        when(stayClient.getStayById(stayId)).thenReturn(new StayResponse(stayId, "UNKNOWN"));

        // Act & Assert
        final StayNotFoundException exception = assertThrows(StayNotFoundException.class, () -> {
            orderService.createOrder(request);
        });

        assertNotNull(exception.getMessage());
        verify(stayClient).getStayById(stayId);
        verifyNoInteractions(orderMapper, orderRepository);
    }

    @Test
    void shouldThrowExceptionWhenStayClientFails() {
        // Arrange
        when(stayClient.getStayById(stayId)).thenThrow(org.mockito.Mockito.mock(feign.FeignException.class));

        // Act & Assert
        final StayNotFoundException exception = assertThrows(StayNotFoundException.class, () -> {
            orderService.createOrder(request);
        });

        assertNotNull(exception.getMessage());
        verify(stayClient).getStayById(stayId);
        verifyNoInteractions(orderMapper, orderRepository);
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
