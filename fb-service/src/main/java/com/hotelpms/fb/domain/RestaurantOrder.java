package com.hotelpms.fb.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a restaurant order in the food and beverage service.
 */
@Entity
@Table(name = "restaurant_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE restaurant_orders SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class RestaurantOrder {

    /** Max length for status column. */
    public static final int MAX_STATUS_LENGTH = 20;

    /** Unique identifier for the Order. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stay ID associated with the order. */
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    /** Date and time the order was placed. */
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    /** Total amount for the order. */
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    /** Status of the order. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = MAX_STATUS_LENGTH)
    private OrderStatus status;

    /** Items included in the order. */
    @OneToMany(mappedBy = "restaurantOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OrderItem> items = new ArrayList<>();

    /** The timestamp when the record was created. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The timestamp when the record was last updated. */
    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    /** Flag indicating whether the record is active (for soft deletion). */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Helper method to add an item to the order and manage the bidirectional
     * relationship.
     *
     * @param item the item to add
     */
    public void addItem(final OrderItem item) {
        items.add(item);
        item.setRestaurantOrder(this);
    }

    /**
     * Helper method to remove an item from the order and manage the bidirectional
     * relationship.
     *
     * @param item the item to remove
     */
    public void removeItem(final OrderItem item) {
        items.remove(item);
        item.setRestaurantOrder(null);
    }
}
