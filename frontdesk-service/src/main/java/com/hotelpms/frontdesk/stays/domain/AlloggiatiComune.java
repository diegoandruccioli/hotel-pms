package com.hotelpms.frontdesk.stays.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Lookup entity for the Portale Alloggiati Web "Comuni" table.
 * Contains the 7 499 official municipality codes.
 */
@Entity
@Table(name = "alloggiati_comuni")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlloggiatiComune {

    private static final int CODICE_LENGTH = 9;

    /**
     * 9-character official municipality code.
     */
    @Id
    @Column(name = "codice", length = CODICE_LENGTH, nullable = false)
    private String codice;

    /**
     * Human-readable name of the municipality.
     */
    @Column(name = "descrizione", length = 100, nullable = false)
    private String descrizione;

    /**
     * 2-character province abbreviation (e.g. {@code "RM"} for Rome).
     */
    @Column(name = "provincia", length = 2, nullable = false)
    private String provincia;

    /**
     * Optional expiry date; {@code null} means the record is still active.
     */
    @Column(name = "data_fine_val")
    private LocalDate dataFineVal;
}
