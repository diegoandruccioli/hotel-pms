package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.domain.MenuItem;
import com.hotelpms.fb.dto.MenuItemRequest;
import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.exception.MenuItemNotFoundException;
import com.hotelpms.fb.repository.MenuItemRepository;
import com.hotelpms.fb.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link MenuItemService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuItemServiceImpl implements MenuItemService {

    private static final String MSG_NOT_FOUND = "MENU_ITEM_NOT_FOUND";

    private final MenuItemRepository menuItemRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAll(final UUID hotelId) {
        log.debug("Fetching active menu items for hotel={}", hotelId);
        return menuItemRepository.findAllByHotelId(hotelId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public MenuItemResponse create(final UUID hotelId, final MenuItemRequest request) {
        final MenuItem item = MenuItem.builder()
                .hotelId(hotelId)
                .name(request.name())
                .price(request.price())
                .category(request.category())
                .description(request.description())
                .available(request.available())
                .build();
        final MenuItem saved = menuItemRepository.save(item);
        log.info("[FB] MENU_ITEM_CREATED | id={} | hotel={} | name={}", saved.getId(), hotelId, saved.getName());
        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public MenuItemResponse update(final UUID hotelId, final UUID itemId, final MenuItemRequest request) {
        final MenuItem item = menuItemRepository.findByIdAndHotelId(itemId, hotelId)
                .orElseThrow(() -> new MenuItemNotFoundException(MSG_NOT_FOUND));
        item.setName(request.name());
        item.setPrice(request.price());
        item.setCategory(request.category());
        item.setDescription(request.description());
        item.setAvailable(request.available());
        final MenuItem saved = menuItemRepository.save(item);
        log.info("[FB] MENU_ITEM_UPDATED | id={} | hotel={}", itemId, hotelId);
        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(final UUID hotelId, final UUID itemId) {
        final MenuItem item = menuItemRepository.findByIdAndHotelId(itemId, hotelId)
                .orElseThrow(() -> new MenuItemNotFoundException(MSG_NOT_FOUND));
        menuItemRepository.delete(item);
        log.info("[FB] MENU_ITEM_DELETED | id={} | hotel={}", itemId, hotelId);
    }

    private MenuItemResponse toResponse(final MenuItem item) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .category(item.getCategory())
                .description(item.getDescription())
                .available(item.isAvailable())
                .build();
    }
}
