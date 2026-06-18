package com.hotelpms.frontdesk.stays.dto;

/**
 * Represents a single guest record in the Italian Alloggiati Web police report.
 * Field names and semantics follow the official 168-character fixed-width tracciato.
 *
 * @param tipoAlloggiato    2-char TIPALLOG code (e.g. "16" OSPITE_SINGOLO)
 * @param dataArrivo        arrival date as {@code dd/MM/yyyy}
 * @param permanenza        number of nights (1-30)
 * @param cognome           surname — 50 chars max
 * @param nome              given name — 30 chars max
 * @param sesso             gender: "1" = Maschio, "2" = Femmina
 * @param dataNascita       date of birth as {@code dd/MM/yyyy}
 * @param comuneNascita     9-char municipality code (Italian-born only, blank otherwise)
 * @param provinciaNascita  2-char province abbreviation (Italian-born only, blank otherwise)
 * @param statoNascita      9-char country-of-birth code (always populated)
 * @param cittadinanza      9-char citizenship state code (always populated)
 * @param tipoDocumento     5-char document-type code (blank for FAMILIARE/MEMBRO_GRUPPO)
 * @param numeroDocumento   document number — 20 chars max (blank for FAMILIARE/MEMBRO_GRUPPO)
 * @param luogoRilascioDoc  9-char issue-place code — comune if Italian, stato if foreign
 *                          (blank for FAMILIARE/MEMBRO_GRUPPO)
 */
public record AlloggiatiRowDto(
        String tipoAlloggiato,
        String dataArrivo,
        int permanenza,
        String cognome,
        String nome,
        String sesso,
        String dataNascita,
        String comuneNascita,
        String provinciaNascita,
        String statoNascita,
        String cittadinanza,
        String tipoDocumento,
        String numeroDocumento,
        String luogoRilascioDoc) {
}
