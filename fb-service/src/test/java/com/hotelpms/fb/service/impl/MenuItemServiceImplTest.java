package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.domain.MenuItem;
import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceImplTest {

    private static final String ITEM_NAME_ESPRESSO = "Espresso";
    private static final String ITEM_NAME_CAPPUCCINO = "Cappuccino";
    private static final String PRICE_250 = "2.50";
    private static final String PRICE_300 = "3.00";

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuItemServiceImpl menuItemService;

    @Test
    void shouldReturnAllActiveMenuItems() {
        final UUID id1 = UUID.randomUUID();
        final UUID id2 = UUID.randomUUID();
        final MenuItem item1 = MenuItem.builder()
                .id(id1).name(ITEM_NAME_ESPRESSO).price(new BigDecimal(PRICE_250)).active(true).build();
        final MenuItem item2 = MenuItem.builder()
                .id(id2).name(ITEM_NAME_CAPPUCCINO).price(new BigDecimal(PRICE_300)).active(true).build();

        when(menuItemRepository.findAll()).thenReturn(List.of(item1, item2));

        final List<MenuItemResponse> result = menuItemService.getAll();

        assertEquals(2, result.size());
        assertEquals(id1, result.get(0).id());
        assertEquals(ITEM_NAME_ESPRESSO, result.get(0).name());
        assertEquals(new BigDecimal(PRICE_250), result.get(0).price());
        assertEquals(id2, result.get(1).id());
        assertEquals(ITEM_NAME_CAPPUCCINO, result.get(1).name());
        assertEquals(new BigDecimal(PRICE_300), result.get(1).price());
    }

    @Test
    void shouldReturnEmptyListWhenNoMenuItems() {
        when(menuItemRepository.findAll()).thenReturn(List.of());

        final List<MenuItemResponse> result = menuItemService.getAll();

        assertTrue(result.isEmpty());
    }
}
