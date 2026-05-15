package com.hotelpms.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.fb.domain.OrderStatus;
import com.hotelpms.fb.dto.OrderItemRequest;
import com.hotelpms.fb.dto.RestaurantOrderRequest;
import com.hotelpms.fb.dto.RestaurantOrderResponse;
import com.hotelpms.fb.exception.GlobalExceptionHandler;
import com.hotelpms.fb.exception.OrderNotFoundException;
import com.hotelpms.fb.exception.StayNotFoundException;
import com.hotelpms.fb.service.RestaurantOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class RestaurantOrderControllerTest {

    private static final String BASE_URL = "/api/v1/fb/orders";
    private static final String PATH_CONFIRM = "/{id}/confirm";
    private static final String PATH_BY_STAY = "/stay/{stayId}";
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STAY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MENU_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final BigDecimal AMOUNT_30 = BigDecimal.valueOf(30);

    @Mock
    private RestaurantOrderService orderService;

    @InjectMocks
    private RestaurantOrderController orderController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private RestaurantOrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        orderResponse = RestaurantOrderResponse.builder()
                .id(ORDER_ID)
                .stayId(STAY_ID)
                .orderDate(LocalDateTime.now())
                .totalAmount(AMOUNT_30)
                .status(OrderStatus.PENDING)
                .items(List.of())
                .build();
    }

    @Test
    void shouldCreateOrderReturn201() throws Exception {
        final OrderItemRequest item = new OrderItemRequest(MENU_ITEM_ID, 2);
        final RestaurantOrderRequest request = new RestaurantOrderRequest(STAY_ID, List.of(item));
        when(orderService.createOrder(any(RestaurantOrderRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldCreateOrderReturn400WhenStayIdMissing() throws Exception {
        final String body = "{\"items\":[{\"menuItemId\":\"" + MENU_ITEM_ID + "\",\"quantity\":1}]}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateOrderReturn400WhenItemsEmpty() throws Exception {
        final String body = "{\"stayId\":\"" + STAY_ID + "\",\"items\":[]}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateOrderReturn404WhenStayNotFound() throws Exception {
        final OrderItemRequest item = new OrderItemRequest(MENU_ITEM_ID, 1);
        final RestaurantOrderRequest request = new RestaurantOrderRequest(STAY_ID, List.of(item));
        when(orderService.createOrder(any(RestaurantOrderRequest.class)))
                .thenThrow(new StayNotFoundException("STAY_NOT_FOUND"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetOrdersByStayReturn200() throws Exception {
        when(orderService.getOrdersByStayId(STAY_ID)).thenReturn(List.of(orderResponse));

        mockMvc.perform(get(BASE_URL + PATH_BY_STAY, STAY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ORDER_ID.toString()));
    }

    @Test
    void shouldGetAllOrdersReturn200() throws Exception {
        final Page<RestaurantOrderResponse> page = new PageImpl<>(
                List.of(orderResponse), PageRequest.of(0, 20), 1L);
        when(orderService.getAllOrders(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());
    }

    @Test
    void shouldConfirmOrderReturn200() throws Exception {
        final RestaurantOrderResponse confirmedResponse = RestaurantOrderResponse.builder()
                .id(ORDER_ID)
                .stayId(STAY_ID)
                .orderDate(LocalDateTime.now())
                .totalAmount(AMOUNT_30)
                .status(OrderStatus.BILLED_TO_ROOM)
                .items(List.of())
                .build();
        when(orderService.confirmOrder(eq(ORDER_ID))).thenReturn(confirmedResponse);

        mockMvc.perform(post(BASE_URL + PATH_CONFIRM, ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BILLED_TO_ROOM"));
    }

    @Test
    void shouldConfirmOrderReturn404WhenOrderNotFound() throws Exception {
        when(orderService.confirmOrder(eq(ORDER_ID)))
                .thenThrow(new OrderNotFoundException("ORDER_NOT_FOUND"));

        mockMvc.perform(post(BASE_URL + PATH_CONFIRM, ORDER_ID))
                .andExpect(status().isNotFound());
    }
}
