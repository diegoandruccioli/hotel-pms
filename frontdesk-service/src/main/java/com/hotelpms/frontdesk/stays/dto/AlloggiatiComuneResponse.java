package com.hotelpms.frontdesk.stays.dto;

import java.time.LocalDate;

/**
 * A Portale Alloggiati Web municipality (comune) as exposed to the frontend.
 *
 * @param codice      the Alloggiati municipality code
 * @param descrizione the municipality name
 * @param provincia   the 2-character province code
 * @param dataFineVal the date the code stops being valid, or {@code null} if still valid
 */
public record AlloggiatiComuneResponse(String codice, String descrizione, String provincia, LocalDate dataFineVal) {
}
