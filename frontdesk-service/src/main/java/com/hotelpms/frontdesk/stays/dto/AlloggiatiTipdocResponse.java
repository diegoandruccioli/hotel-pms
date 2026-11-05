package com.hotelpms.frontdesk.stays.dto;

/**
 * A Portale Alloggiati Web document-type code (tipdoc) as exposed to the frontend.
 *
 * @param codice      the document-type code
 * @param descrizione the document-type description
 */
public record AlloggiatiTipdocResponse(String codice, String descrizione) {
}
