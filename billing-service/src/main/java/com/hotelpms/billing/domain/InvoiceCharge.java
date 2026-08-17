package com.hotelpms.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single line-item charge on an invoice (room night, F&amp;B order, extra).
 * Charges are immutable once added; the parent Invoice owns the aggregate.
 */
@Entity
@Table(name = "invoice_charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class InvoiceCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "vat_rate", nullable = false)
    private BigDecimal vatRate;

    /**
     * FatturaPA {@code Natura} code (e.g. {@code "N1"}) for a charge that is out of
     * VAT scope — {@code null} for every ordinary taxable charge. When set,
     * {@code vatRate} must be zero (enforced by {@code chk_charges_natura_requires_zero_rate}).
     */
    @Column(name = "natura_code")
    private String naturaCode;

    @Column(name = "reference_id")
    private UUID referenceId;

    /**
     * Optional per-night price, display/audit only — {@code amount} above
     * remains the sole fiscally authoritative field. {@code null} when the
     * caller has no single uniform per-night rate to report.
     */
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    /**
     * Optional number of nights this charge covers, display/audit only.
     */
    @Column(name = "nights")
    private Integer nights;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
