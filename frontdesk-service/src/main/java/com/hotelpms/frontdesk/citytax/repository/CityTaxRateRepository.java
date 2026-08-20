package com.hotelpms.frontdesk.citytax.repository;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CityTaxRate}.
 */
@Repository
public interface CityTaxRateRepository extends JpaRepository<CityTaxRate, UUID> {

    /**
     * Finds a rate rule by id, scoped to a hotel — a rule belonging to another
     * hotel is never visible (same T-ROOM-02 pattern as every other tenant-root
     * lookup in this service).
     *
     * @param id      the rate rule UUID
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the rule, if it exists and belongs to this hotel
     */
    Optional<CityTaxRate> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Lists every rate rule for a hotel, most recent {@code validFrom} first —
     * both the current rule and its history, for the admin listing screen.
     *
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the rules, newest first
     */
    List<CityTaxRate> findAllByHotelIdOrderByValidFromDesc(UUID hotelId);

    /**
     * Resolves the rate rule applicable for a hotel's (comune, category) pair on
     * a given date — at most one row can ever match, guaranteed by
     * {@code excl_city_tax_rates_no_overlap}.
     *
     * @param hotelId      the hotel UUID (multi-tenant scoping)
     * @param comuneCodice the comune's stable code
     * @param category     the hotel's classification at the relevant date
     * @param date         the date to resolve a rule for (typically the stay's check-in date)
     * @return the covering rule, if any
     */
    @Query("SELECT r FROM CityTaxRate r WHERE r.hotelId = :hotelId AND r.comuneCodice = :comuneCodice "
            + "AND r.category = :category AND r.validFrom <= :date "
            + "AND (r.validTo IS NULL OR r.validTo > :date)")
    Optional<CityTaxRate> findApplicableByHotelId(
            @Param("hotelId") UUID hotelId,
            @Param("comuneCodice") String comuneCodice,
            @Param("category") String category,
            @Param("date") LocalDate date);

    /**
     * Finds the currently open-ended rule (if any) for a hotel's (comune,
     * category) pair — the row a new rule creation must close by setting its
     * {@code validTo} before inserting the new one.
     *
     * @param hotelId      the hotel UUID (multi-tenant scoping)
     * @param comuneCodice the comune's stable code
     * @param category     the classification the new rule is being created for
     * @return the open-ended rule, if any
     */
    Optional<CityTaxRate> findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(
            UUID hotelId, String comuneCodice, String category);
}
