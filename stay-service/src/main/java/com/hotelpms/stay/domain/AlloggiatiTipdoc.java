package com.hotelpms.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lookup entity for the Portale Alloggiati Web "TipDoc" table.
 * Contains the 95 official document-type codes.
 */
@Entity
@Table(name = "alloggiati_tipdoc")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlloggiatiTipdoc {

    private static final int CODICE_LENGTH = 5;

    /**
     * 5-character official document-type code (e.g. {@code "PASOR"} for ordinary passport).
     */
    @Id
    @Column(name = "codice", length = CODICE_LENGTH, nullable = false)
    private String codice;

    /**
     * Human-readable description of the document type.
     */
    @Column(name = "descrizione", length = 100, nullable = false)
    private String descrizione;
}
