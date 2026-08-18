package com.hotelpms.frontdesk.stays.repository;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link AlloggiatiComune} lookup records.
 */
@Repository
public interface AlloggiatiComuneRepository extends JpaRepository<AlloggiatiComune, String> {

    /** Named-parameter key shared by the {@code :today} placeholder across the queries below. */
    String TODAY_PARAM = "today";

    /** Named-parameter key shared by the {@code :provincia} placeholder across the queries below. */
    String PROVINCIA_PARAM = "provincia";

    /**
     * Returns all comuni whose validity has not expired.
     *
     * @param today today's date used as the upper bound for expiry check
     * @return list of active comuni
     */
    @Query("SELECT c FROM AlloggiatiComune c WHERE c.dataFineVal IS NULL OR c.dataFineVal > :today")
    List<AlloggiatiComune> findActive(@Param(TODAY_PARAM) LocalDate today);

    /**
     * Returns comuni for a given province whose validity has not expired.
     *
     * @param provincia the 2-character province code
     * @param today     today's date used as the upper bound for expiry check
     * @return list of active comuni for the province
     */
    @Query("SELECT c FROM AlloggiatiComune c WHERE c.provincia = :provincia "
            + "AND (c.dataFineVal IS NULL OR c.dataFineVal > :today)")
    List<AlloggiatiComune> findActiveByProvincia(
            @Param(PROVINCIA_PARAM) String provincia,
            @Param(TODAY_PARAM) LocalDate today);

    /**
     * Full-text search on comune name, optionally filtered by province.
     * Results are ordered by match quality (exact match, then prefix match, then substring
     * match) before falling back to alphabetical order, then limited by the supplied
     * {@link Pageable} — otherwise a common comune (e.g. "Roma") can be pushed past the cap
     * by alphabetically earlier substring matches (e.g. "Arcinazzo Romano").
     *
     * @param term      search term (case-insensitive substring match on descrizione)
     * @param provincia optional 2-character province filter (pass {@code null} to skip)
     * @param today     today's date used for expiry check
     * @param pageable  pagination to cap result size (use {@code PageRequest.of(0, 20)})
     * @return list of matching active comuni
     */
    @Query("SELECT c FROM AlloggiatiComune c "
            + "WHERE LOWER(c.descrizione) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "AND (c.dataFineVal IS NULL OR c.dataFineVal > :today) "
            + "AND (:provincia IS NULL OR c.provincia = :provincia) "
            + "ORDER BY CASE "
            + "WHEN LOWER(c.descrizione) = LOWER(:term) THEN 0 "
            + "WHEN LOWER(c.descrizione) LIKE LOWER(CONCAT(:term, '%')) THEN 1 "
            + "ELSE 2 END, c.descrizione")
    List<AlloggiatiComune> searchActive(
            @Param("term") String term,
            @Param(PROVINCIA_PARAM) String provincia,
            @Param(TODAY_PARAM) LocalDate today,
            Pageable pageable);

    /**
     * Checks whether an active comune with the given name exists in the given province —
     * used to validate the hotel/guest structured address before it can be used in a
     * FatturaPA XML export (P0-1).
     *
     * @param comune    the comune name (case-insensitive exact match)
     * @param provincia the 2-character province code
     * @param today     today's date used for expiry check
     * @return {@code true} if a matching active comune exists
     */
    @Query("SELECT COUNT(c) > 0 FROM AlloggiatiComune c "
            + "WHERE LOWER(c.descrizione) = LOWER(:comune) AND c.provincia = :provincia "
            + "AND (c.dataFineVal IS NULL OR c.dataFineVal > :today)")
    boolean existsActiveByComuneAndProvincia(
            @Param("comune") String comune,
            @Param(PROVINCIA_PARAM) String provincia,
            @Param(TODAY_PARAM) LocalDate today);

    /**
     * Resolves the stable {@code codice} for an active comune by name+provincia —
     * same match as {@link #existsActiveByComuneAndProvincia}, but returning the
     * row itself so callers (E18: {@code HotelSettingsServiceImpl}) can store the
     * code instead of only confirming existence.
     *
     * @param comune    the comune name (case-insensitive exact match)
     * @param provincia the 2-character province code
     * @param today     today's date used for expiry check
     * @return the matching active comune, if any
     */
    @Query("SELECT c FROM AlloggiatiComune c "
            + "WHERE LOWER(c.descrizione) = LOWER(:comune) AND c.provincia = :provincia "
            + "AND (c.dataFineVal IS NULL OR c.dataFineVal > :today)")
    Optional<AlloggiatiComune> findActiveByComuneAndProvincia(
            @Param("comune") String comune,
            @Param(PROVINCIA_PARAM) String provincia,
            @Param(TODAY_PARAM) LocalDate today);
}
