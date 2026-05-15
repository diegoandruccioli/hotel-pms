package com.hotelpms.fb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a menu item in the food and beverage catalog.
 *
 * <p>Prices are defined server-side; clients reference items by UUID.
 * This prevents client-side price tampering (T-FB-02).
 */
@Entity
@Table(name = "menu_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE menu_items SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class MenuItem {

    /** Unique identifier for the menu item. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Hotel scope for multi-tenant isolation — set server-side, never from client. */
    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    /** Display name of the item (e.g. "Espresso", "Club Sandwich"). */
    @Column(nullable = false)
    private String name;

    /** Canonical price in EUR, set by hotel management — never by the client. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Display category (e.g. "Bar", "Cucina", "Dessert"). */
    @Column(nullable = false, length = 100)
    private String category;

    /** Optional free-text description. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Visibility for ordering; false hides from order form without deleting. */
    @Column(nullable = false)
    @Builder.Default
    private boolean available = true;

    /** Soft-delete flag. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Record creation timestamp, managed by Spring Data Auditing. */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last modification timestamp, managed by Spring Data Auditing. */
    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime updatedAt;
}
