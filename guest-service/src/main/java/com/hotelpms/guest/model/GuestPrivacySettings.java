package com.hotelpms.guest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-hotel GDPR data-retention settings (T-GST-05).
 * One row per hotel; {@code hotel_id} is the natural primary key.
 *
 * <p>The {@code guestRetentionYears} value is the hotel's preferred minimum
 * period (in years, from the guest's last stay) before the profile may be
 * anonymised. Application-layer validation guarantees it is never set below
 * the TULPS legal minimum of five years.
 */
@Entity
@Table(name = "guest_privacy_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class GuestPrivacySettings {

    /** Minimum retention period mandated by TULPS (Alloggiati Web data). */
    public static final int TULPS_MIN_YEARS = 5;

    /** Minimum retention period mandated by Codice Civile art. 2220 (fiscal). */
    public static final int FISCAL_MIN_YEARS = 10;

    @Id
    @Column(name = "hotel_id")
    private UUID hotelId;

    /**
     * Hotel's preferred guest retention period in years from last stay.
     * Must be {@code >= TULPS_MIN_YEARS}. Validated at the service layer.
     */
    @Builder.Default
    @Column(name = "guest_retention_years", nullable = false)
    private int guestRetentionYears = TULPS_MIN_YEARS;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;
}
