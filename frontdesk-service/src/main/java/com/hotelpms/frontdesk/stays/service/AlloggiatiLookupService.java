package com.hotelpms.frontdesk.stays.service;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiStato;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiTipdoc;

import java.util.List;
import java.util.Optional;

/**
 * Service for querying Portale Alloggiati Web lookup data
 * (stati, comuni, tipdoc).
 */
public interface AlloggiatiLookupService {

    /**
     * Returns all active stati (country codes).
     *
     * @return list of active stati
     */
    List<AlloggiatiStato> findAllStati();

    /**
     * Returns all active comuni (municipalities).
     *
     * @return list of active comuni
     */
    List<AlloggiatiComune> findAllComuni();

    /**
     * Returns all active comuni for the given province.
     *
     * @param provincia 2-character province code
     * @return list of active comuni in that province
     */
    List<AlloggiatiComune> findComuniByProvincia(String provincia);

    /**
     * Returns all tipdoc entries (document-type codes).
     *
     * @return list of all tipdoc
     */
    List<AlloggiatiTipdoc> findAllTipdoc();

    /**
     * Finds a stato by its 9-character code.
     *
     * @param codice the state code
     * @return an {@link Optional} containing the stato if found
     */
    Optional<AlloggiatiStato> findStatoByCodice(String codice);

    /**
     * Finds a comune by its 9-character code.
     *
     * @param codice the municipality code
     * @return an {@link Optional} containing the comune if found
     */
    Optional<AlloggiatiComune> findComuneByCodice(String codice);

    /**
     * Finds a tipdoc by its 5-character code.
     *
     * @param codice the document-type code
     * @return an {@link Optional} containing the tipdoc if found
     */
    Optional<AlloggiatiTipdoc> findTipdocByCodice(String codice);

    /**
     * Autocomplete search for comuni.
     * Returns at most 20 active comuni whose name contains {@code query} (case-insensitive),
     * optionally filtered by 2-character province code.
     *
     * @param query     substring to match against comune name (must be non-blank)
     * @param provincia optional province filter (may be {@code null})
     * @return ordered list of matching comuni (max 20)
     */
    List<AlloggiatiComune> searchComuni(String query, String provincia);
}
