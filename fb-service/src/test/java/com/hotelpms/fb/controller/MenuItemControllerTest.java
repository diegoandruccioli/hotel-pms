package com.hotelpms.fb.controller;

import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.exception.GlobalExceptionHandler;
import com.hotelpms.fb.service.MenuItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MenuItemControllerTest {

    private static final String BASE_URL = "/api/v1/fb/menu-items";

    @Mock
    private MenuItemService menuItemService;

    @InjectMocks
    private MenuItemController menuItemController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(menuItemController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldGetAllMenuItemsReturn200() throws Exception {
        final MenuItemResponse item = MenuItemResponse.builder()
                .id(UUID.randomUUID())
                .name("Espresso")
                .price(new BigDecimal("1.50"))
                .build();
        when(menuItemService.getAll()).thenReturn(List.of(item));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Espresso"));
    }

    @Test
    void shouldGetAllMenuItemsReturn200WhenEmpty() throws Exception {
        when(menuItemService.getAll()).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
