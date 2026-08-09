package com.hotelpms.frontdesk.quotations.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
 * One alternative room combination within a {@link Quotation} — a quotation
 * offers 1 to 5 of these for the guest to compare (e.g. "Standard Double" at
 * one price vs "Suite" at another). Conversion picks exactly one.
 */
@Entity
@Table(name = "quotation_options")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE quotation_options SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class QuotationOption {

    private static final int MAX_LABEL_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(nullable = false, length = MAX_LABEL_LENGTH)
    private String label;

    /** Display order, 0-based, as chosen at creation time. */
    @Column(nullable = false)
    private int position;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @OneToMany(mappedBy = "quotationOption", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @SQLRestriction("active = true")
    private List<QuotationLineItem> lineItems = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
