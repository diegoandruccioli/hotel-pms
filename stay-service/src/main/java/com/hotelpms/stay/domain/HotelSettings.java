package com.hotelpms.stay.domain;

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
 * Per-hotel operational settings. One row per hotel; hotel_id is the natural primary key.
 */
@Entity
@Table(name = "hotel_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class HotelSettings {

    /** The hotel this settings row belongs to (primary key). */
    @Id
    @Column(name = "hotel_id")
    private UUID hotelId;

    /**
     * When {@code true}, the Alloggiati Web report is automatically submitted to the
     * Polizia di Stato portal at each check-in.
     */
    @Column(name = "alloggiati_auto_send", nullable = false)
    private boolean alloggiatiAutoSend;

    /** The timestamp when the record was created. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The timestamp when the record was last updated. */
    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;
}
