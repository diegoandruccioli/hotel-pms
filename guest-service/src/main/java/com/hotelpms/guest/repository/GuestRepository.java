package com.hotelpms.guest.repository;

import com.hotelpms.guest.architecture.TenantScopeExempt;
import com.hotelpms.guest.model.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Guest entity.
 *
 * <p>All query methods are scoped to {@code hotelId} to enforce
 * multi-tenant isolation (T-GST-01, T-GST-03). Direct use of the
 * inherited {@code findById} / {@code findAll} is intentionally avoided
 * in the service layer; hotel-scoped variants must be used instead.
 */
@Repository
public interface GuestRepository extends JpaRepository<Guest, UUID> {

    /**
     * Finds an active guest by UUID within the given hotel.
     * Returns {@link Optional#empty()} if the guest exists but belongs to a
     * different hotel, preventing IDOR enumeration.
     *
     * @param id      the guest UUID
     * @param hotelId the owning hotel UUID
     * @return the guest if found and owned by the hotel
     */
    Optional<Guest> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Returns a paginated list of all active guests belonging to a hotel.
     *
     * @param hotelId  the owning hotel UUID
     * @param pageable pagination parameters
     * @return page of guests scoped to the hotel
     */
    Page<Guest> findAllByHotelId(UUID hotelId, Pageable pageable);

    /**
     * Returns all active guests with the given IDs that belong to the hotel.
     * IDs belonging to other hotels are silently excluded.
     *
     * @param ids     the list of guest UUIDs to look up
     * @param hotelId the owning hotel UUID
     * @return list of matching guests scoped to the hotel
     */
    List<Guest> findAllByIdInAndHotelId(List<UUID> ids, UUID hotelId);

    /**
     * Searches guests within a hotel by first name, last name, email or city
     * containing the provided value (case-insensitive).
     *
     * @param keyword  search term matched against name, email, city
     * @param hotelId  the owning hotel UUID
     * @param pageable pagination information
     * @return a page of matching guests scoped to the hotel
     */
    @Query("SELECT g FROM Guest g WHERE g.hotelId = :hotelId AND ("
            + "LOWER(g.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(g.lastName)  LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(g.email)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(g.city)      LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Guest> searchByKeywordAndHotelId(
            @Param("keyword") String keyword,
            @Param("hotelId") UUID hotelId,
            Pageable pageable);

    /**
     * Returns all active guests whose GDPR consent date is strictly before the
     * given cutoff. Used by the nightly retention job as a pre-filter to identify
     * candidates for anonymisation (T-GST-05).
     *
     * <p>The caller must still verify the legal-hold constraints (TULPS and
     * fiscal) before proceeding with anonymisation.
     *
     * @param cutoff the exclusive upper bound for the consent date
     * @return list of candidate guests
     */
    @TenantScopeExempt(reason = "Intentionally platform-wide: the nightly retention job scans "
            + "every hotel for candidates, then GuestRetentionJobServiceImpl verifies the "
            + "per-guest legal-hold constraints (TULPS/fiscal) before ever anonymising anything "
            + "(T-GST-05) — this is a pre-filter, not a data-returning endpoint.")
    List<Guest> findByGdprConsentDateBefore(LocalDate cutoff);
}
