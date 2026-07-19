package com.hotelpms.guest.client.dto;

/**
 * External DTO for a single comune (municipality) entry fetched from the
 * Alloggiati Web reference data owned by frontdesk-service.
 *
 * @param codice      9-character official municipality code
 * @param descrizione the municipality name
 * @param provincia   2-character province code (e.g. {@code "RM"})
 */
public record AlloggiatiComuneClientResponse(String codice, String descrizione, String provincia) {
}
