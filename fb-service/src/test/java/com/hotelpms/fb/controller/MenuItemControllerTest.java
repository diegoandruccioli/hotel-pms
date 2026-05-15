package com.hotelpms.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelpms.fb.dto.MenuItemRequest;
import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.exception.GlobalExceptionHandler;
import com.hotelpms.fb.exception.MenuItemNotFoundException;
import com.hotelpms.fb.service.MenuItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class MenuItemControllerTest {

    private static final String BASE_URL = "/api/v1/fb/menu-items";
    private static final String PATH_ITEM = "/{id}";
    private static final String ITEM_NAME_ESPRESSO = "Espresso";
    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String PRICE_150 = "1.50";
    private static final String CATEGORY_BAR = "Bar";

    @Mock
    private MenuItemService menuItemService;

    @InjectMocks
    private MenuItemController menuItemController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MenuItemResponse sampleResponse;

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        objectMapper = new ObjectMapper();
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(menuItemController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        sampleResponse = MenuItemResponse.builder()
                .id(ITEM_ID).name(ITEM_NAME_ESPRESSO).price(new BigDecimal(PRICE_150))
                .category(CATEGORY_BAR).description(null).available(true).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetAllReturn200() throws Exception {
        when(menuItemService.getAll(HOTEL_ID)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(ITEM_NAME_ESPRESSO))
                .andExpect(jsonPath("$[0].category").value(CATEGORY_BAR));
    }

    @Test
    void shouldGetAllReturn200WhenEmpty() throws Exception {
        when(menuItemService.getAll(HOTEL_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldCreateReturn201() throws Exception {
        final MenuItemRequest request = new MenuItemRequest(
                ITEM_NAME_ESPRESSO, new BigDecimal(PRICE_150), CATEGORY_BAR, null, true);
        when(menuItemService.create(eq(HOTEL_ID), any(MenuItemRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(ITEM_NAME_ESPRESSO));
    }

    @Test
    void shouldCreateReturn400WhenInvalidPayload() throws Exception {
        final String body = "{\"name\":\"\",\"price\":-1,\"category\":\"\",\"available\":true}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateReturn200() throws Exception {
        final MenuItemRequest request = new MenuItemRequest(
                "Espresso Double", new BigDecimal("3.00"), CATEGORY_BAR, "Double shot", true);
        when(menuItemService.update(eq(HOTEL_ID), eq(ITEM_ID), any(MenuItemRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(put(BASE_URL + PATH_ITEM, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateReturn404WhenNotFound() throws Exception {
        final MenuItemRequest request = new MenuItemRequest("X", new BigDecimal(PRICE_150), CATEGORY_BAR, null, true);
        doThrow(new MenuItemNotFoundException("MENU_ITEM_NOT_FOUND"))
                .when(menuItemService).update(eq(HOTEL_ID), eq(ITEM_ID), any(MenuItemRequest.class));

        mockMvc.perform(put(BASE_URL + PATH_ITEM, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteReturn204() throws Exception {
        doNothing().when(menuItemService).delete(HOTEL_ID, ITEM_ID);

        mockMvc.perform(delete(BASE_URL + PATH_ITEM, ITEM_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldDeleteReturn404WhenNotFound() throws Exception {
        doThrow(new MenuItemNotFoundException("MENU_ITEM_NOT_FOUND"))
                .when(menuItemService).delete(HOTEL_ID, ITEM_ID);

        mockMvc.perform(delete(BASE_URL + PATH_ITEM, ITEM_ID))
                .andExpect(status().isNotFound());
    }
}
