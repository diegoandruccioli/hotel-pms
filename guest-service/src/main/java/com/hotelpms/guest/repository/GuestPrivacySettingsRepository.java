package com.hotelpms.guest.repository;

import com.hotelpms.guest.model.GuestPrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for per-hotel GDPR retention settings (T-GST-05).
 */
@Repository
public interface GuestPrivacySettingsRepository extends JpaRepository<GuestPrivacySettings, UUID> {
}
