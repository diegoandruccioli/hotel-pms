package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.domain.MenuItem;
import com.hotelpms.fb.dto.MenuItemRequest;
import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.exception.MenuItemNotFoundException;
import com.hotelpms.fb.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class MenuItemServiceImplTest {

    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOTEL_OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final String ITEM_NAME_ESPRESSO = "Espresso";
    private static final String CATEGORY_BAR = "Bar";
    private static final String PRICE_250 = "2.50";
    private static final String PRICE_300 = "3.00";

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuItemServiceImpl menuItemService;

    private MenuItem espresso;

    @BeforeEach
    void setUp() {
        espresso = MenuItem.builder()
                .id(ITEM_ID)
                .hotelId(HOTEL_ID)
                .name(ITEM_NAME_ESPRESSO)
                .price(new BigDecimal(PRICE_250))
                .category(CATEGORY_BAR)
                .available(true)
                .active(true)
                .build();
    }

    @Test
    void shouldReturnHotelScopedItemsOnGetAll() {
        final MenuItem cappuccino = MenuItem.builder()
                .id(UUID.randomUUID()).hotelId(HOTEL_ID).name("Cappuccino")
                .price(new BigDecimal(PRICE_300)).category(CATEGORY_BAR).available(true).active(true).build();

        when(menuItemRepository.findAllByHotelId(HOTEL_ID)).thenReturn(List.of(espresso, cappuccino));

        final List<MenuItemResponse> result = menuItemService.getAll(HOTEL_ID);

        assertEquals(2, result.size());
        assertEquals(ITEM_NAME_ESPRESSO, result.get(0).name());
        assertEquals(CATEGORY_BAR, result.get(0).category());
        assertTrue(result.get(0).available());
    }

    @Test
    void shouldReturnEmptyListOnGetAllWhenNoItems() {
        when(menuItemRepository.findAllByHotelId(HOTEL_ID)).thenReturn(List.of());

        assertTrue(menuItemService.getAll(HOTEL_ID).isEmpty());
    }

    @Test
    void createShouldPersistWithHotelId() {
        final MenuItemRequest request = new MenuItemRequest(ITEM_NAME_ESPRESSO, new BigDecimal(PRICE_250),
                CATEGORY_BAR, null, true);
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(espresso);

        final MenuItemResponse result = menuItemService.create(HOTEL_ID, request);

        assertEquals(ITEM_NAME_ESPRESSO, result.name());
        assertEquals(CATEGORY_BAR, result.category());
        verify(menuItemRepository).save(any(MenuItem.class));
    }

    @Test
    void updateShouldModifyAndSave() {
        final MenuItemRequest request = new MenuItemRequest("Espresso Double", new BigDecimal(PRICE_300),
                CATEGORY_BAR, "Double shot", false);
        when(menuItemRepository.findByIdAndHotelId(ITEM_ID, HOTEL_ID)).thenReturn(Optional.of(espresso));
        when(menuItemRepository.save(espresso)).thenReturn(espresso);

        final MenuItemResponse result = menuItemService.update(HOTEL_ID, ITEM_ID, request);

        assertEquals("Espresso Double", result.name());
        verify(menuItemRepository).save(espresso);
    }

    @Test
    void updateShouldThrowWhenNotFoundOrWrongHotel() {
        when(menuItemRepository.findByIdAndHotelId(ITEM_ID, HOTEL_OTHER)).thenReturn(Optional.empty());

        assertThrows(MenuItemNotFoundException.class,
                () -> menuItemService.update(HOTEL_OTHER, ITEM_ID, new MenuItemRequest(
                        ITEM_NAME_ESPRESSO, new BigDecimal(PRICE_250), CATEGORY_BAR, null, true)));
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void deleteShouldSoftDeleteItem() {
        when(menuItemRepository.findByIdAndHotelId(ITEM_ID, HOTEL_ID)).thenReturn(Optional.of(espresso));

        menuItemService.delete(HOTEL_ID, ITEM_ID);

        verify(menuItemRepository).delete(espresso);
    }

    @Test
    void deleteShouldThrowWhenNotFoundOrWrongHotel() {
        when(menuItemRepository.findByIdAndHotelId(ITEM_ID, HOTEL_OTHER)).thenReturn(Optional.empty());

        assertThrows(MenuItemNotFoundException.class,
                () -> menuItemService.delete(HOTEL_OTHER, ITEM_ID));
        verify(menuItemRepository, never()).delete(any(MenuItem.class));
    }
}
