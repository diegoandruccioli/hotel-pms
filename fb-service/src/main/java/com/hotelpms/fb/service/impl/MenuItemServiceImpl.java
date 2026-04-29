package com.hotelpms.fb.service.impl;

import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.repository.MenuItemRepository;
import com.hotelpms.fb.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link MenuItemService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAll() {
        log.debug("Fetching all active menu items");
        return menuItemRepository.findAll().stream()
                .map(item -> MenuItemResponse.builder()
                        .id(item.getId())
                        .name(item.getName())
                        .price(item.getPrice())
                        .build())
                .toList();
    }
}
