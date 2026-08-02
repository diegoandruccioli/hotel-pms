package com.hotelpms.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable record of a single FatturaPA XML export actually generated for an invoice.
 * One row per export: exact payload bytes, SHA-256 hash, and timestamp — so a later
 * regeneration of the XML can never be silently assumed identical to what was
 * historically produced and handed to the commercialista/third-party software.
 *
 * <p>No soft-delete, no update methods: rows here are never modified or removed once
 * written. Existence of at least one row for an invoice is also what drives the
 * post-export immutability guard on {@code Invoice} in {@code InvoiceServiceImpl}.
 */
@Entity
@Table(name = "invoice_fiscal_exports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class InvoiceFiscalExport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "hotel_id")
    private UUID hotelId;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;

    // VARBINARY (not @Lob, which maps byte[] to a Postgres large-object OID) matches
    // the bytea column type declared in V11 — a plain byte string, not an LO reference.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "xml_payload", nullable = false)
    private byte[] xmlPayload;

    @Column(name = "payload_sha256", nullable = false)
    private String payloadSha256;

    @Column(name = "exported_by", nullable = false)
    private String exportedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
