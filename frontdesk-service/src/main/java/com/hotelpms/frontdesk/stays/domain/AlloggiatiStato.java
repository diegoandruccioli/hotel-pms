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
 * Lookup entity for the Portale Alloggiati Web "Stati" table.
 * Contains the 249 official country codes and descriptions.
 */
@Entity
@Table(name = "alloggiati_stati")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlloggiatiStato {

    private static final int CODICE_LENGTH = 9;

    /**
     * 9-character official state code (e.g. {@code "100000100"} for Italy).
     */
    @Id
    @Column(name = "codice", length = CODICE_LENGTH, nullable = false)
    private String codice;

    /**
     * Human-readable description of the state.
     */
    @Column(name = "descrizione", length = 100, nullable = false)
    private String descrizione;

    /**
     * Optional expiry date; {@code null} means the record is still active.
     */
    @Column(name = "data_fine_val")
    private LocalDate dataFineVal;
}
