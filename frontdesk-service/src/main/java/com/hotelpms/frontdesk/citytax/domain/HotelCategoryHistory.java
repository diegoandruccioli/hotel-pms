package com.hotelpms.frontdesk.citytax.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A hotel's classification/category (stelle) for a period of time. Append-only
 * and versioned by date range — a hotel that changes category must have past
 * stays assessed against the category it actually held on the stay's check-in
 * date, not its current one, so this is never mutated in place: an upgrade
 * closes the current row ({@code validTo}) and inserts a new one.
 *
 * <p>{@code category} is a free-form label, not a fixed enum: classification
 * schemes are not standardized nationally — the exact values in use are
 * whatever a given comune's regolamento names (star ratings for hotels, but
 * comuni also tax B&amp;B/case vacanza/campeggi under different labels).
 */
@Entity
@Table(name = "hotel_category_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class HotelCategoryHistory {

    private static final int MAX_CATEGORY_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(nullable = false, length = MAX_CATEGORY_LENGTH)
    private String category;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** {@code null} = this is the hotel's current category. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
