package com.hotelpms.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Contatore per la numerazione progressiva delle fatture.
 * Una riga per (hotel_id, anno solare) — aggiornata con lock pessimistico
 * per garantire numeri senza lacune e senza duplicati anche sotto carico concorrente.
 */
@Entity
@Table(name = "invoice_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSequence {

    @EmbeddedId
    private InvoiceSequenceId id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    /**
     * Factory per la prima fattura di un hotel in un determinato anno.
     *
     * @param hotelId hotel tenant
     * @param year    anno solare (es. 2026)
     * @return nuova sequenza con lastSeq = 0
     */
    public static InvoiceSequence startFor(final UUID hotelId, final int year) {
        return InvoiceSequence.builder()
                .id(new InvoiceSequenceId(hotelId, year))
                .lastSeq(0L)
                .build();
    }
}
