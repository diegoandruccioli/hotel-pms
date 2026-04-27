package com.hotelpms.stay.repository;

import com.hotelpms.stay.domain.HotelSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for per-hotel operational settings.
 */
public interface HotelSettingsRepository extends JpaRepository<HotelSettings, UUID> {
}
