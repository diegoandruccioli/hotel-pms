package com.hotelpms.stay.dto;

/**
 * Represents a single data row in an Italian Alloggiati Web police report.
 * Fields follow the standard order: Cognome, Nome, DataNascita,
 * TipoDocumento, NumeroDocumento, DataArrivo.
 *
 * @param lastName       guest's surname
 * @param firstName      guest's given name
 * @param dateOfBirth    guest's date of birth formatted as DD/MM/YYYY (or "N/A"
 *                       if absent)
 * @param documentType   type of ID document (e.g. PASSPORT)
 * @param documentNumber identification document number
 * @param arrivalDate    check-in date formatted as DD/MM/YYYY
 */
public record AlloggiatiRowDto(
        String lastName,
        String firstName,
        String dateOfBirth,
        String documentType,
        String documentNumber,
        String arrivalDate) {
}
