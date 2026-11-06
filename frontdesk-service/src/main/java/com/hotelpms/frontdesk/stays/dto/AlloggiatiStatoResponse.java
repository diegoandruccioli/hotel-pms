package com.hotelpms.frontdesk.stays.dto;

import java.time.LocalDate;

/**
 * A Portale Alloggiati Web country code (stato) as exposed to the frontend.
 *
 * @param codice      the Alloggiati country code
 * @param descrizione the country name
 * @param dataFineVal the date the code stops being valid, or {@code null} if still valid
 */
public record AlloggiatiStatoResponse(String codice, String descrizione, LocalDate dataFineVal) {
}
