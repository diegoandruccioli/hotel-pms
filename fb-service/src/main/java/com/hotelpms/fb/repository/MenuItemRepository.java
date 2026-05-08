package com.hotelpms.fb.repository;

import com.hotelpms.fb.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for MenuItem.
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
}
