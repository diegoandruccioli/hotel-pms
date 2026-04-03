package com.hotelpms.fb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.util.UUID;

/**
 * Entity representing an item within a restaurant order.
 */
@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE order_items SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class OrderItem {

    /** Unique identifier for the OrderItem. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The restaurant order this item belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RestaurantOrder restaurantOrder;

    /** Name of the item. */
    @Column(name = "item_name", nullable = false)
    private String itemName;

    /** Quantity of the item. */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Unit price of the item. */
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

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
}
