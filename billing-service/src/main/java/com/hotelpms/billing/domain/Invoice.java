package com.hotelpms.billing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
 * Represents an invoice in the billing domain.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uq_invoices_hotel_invoice_number",
                columnNames = {"hotel_id", "invoice_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE invoices SET active = false WHERE id = ?")
@SQLRestriction("active = true")
@NamedEntityGraph(
        name = "Invoice.withDetails",
        attributeNodes = {
            @NamedAttributeNode("charges"),
            @NamedAttributeNode("payments")
        })
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "hotel_id")
    private UUID hotelId;

    // Uniqueness is per (hotel_id, invoice_number), not invoice_number alone — see the
    // @Table uniqueConstraints above. Two hotels both issuing "2026/0001" is expected
    // (V5__add_invoice_sequence.sql), so this column intentionally has no unique=true.
    @Column(nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private LocalDateTime issueDate;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType = DocumentType.FATTURA;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "sdi_status", nullable = false)
    private SdiStatus sdiStatus = SdiStatus.NOT_SENT;

    private UUID reservationId;

    @Column(nullable = false)
    private UUID guestId;

    @Column(name = "stay_id")
    private UUID stayId;

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<InvoiceCharge> charges = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Adds a payment to the invoice.
     *
     * @param payment the payment to add
     */
    public void addPayment(final Payment payment) {
        payments.add(payment);
        payment.setInvoice(this);
    }

    /**
     * Removes a payment from the invoice.
     *
     * @param payment the payment to remove
     */
    public void removePayment(final Payment payment) {
        payments.remove(payment);
        payment.setInvoice(null);
    }

    /**
     * Adds a charge to the invoice, maintaining bidirectional consistency.
     *
     * @param charge the charge to add
     */
    public void addCharge(final InvoiceCharge charge) {
        charges.add(charge);
        charge.setInvoice(this);
    }

    /**
     * Removes a charge from the invoice, maintaining bidirectional consistency.
     *
     * @param charge the charge to remove
     */
    public void removeCharge(final InvoiceCharge charge) {
        charges.remove(charge);
        charge.setInvoice(null);
    }
}
