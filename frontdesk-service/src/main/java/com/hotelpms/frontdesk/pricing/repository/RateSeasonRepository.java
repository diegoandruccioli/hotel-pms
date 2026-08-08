package com.hotelpms.frontdesk.pricing.repository;

import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RateSeason.
 */
@Repository
public interface RateSeasonRepository extends JpaRepository<RateSeason, UUID> {

    /**
     * Finds the active season covering {@code date} for a room type, scoped to a
     * hotel. At most one row can ever match — {@code excl_rate_seasons_no_overlap}
     * guarantees active seasons for the same room type never overlap.
     *
     * @param roomTypeId the room type UUID
     * @param hotelId    the hotel UUID (multi-tenant scoping)
     * @param date       the date to resolve a price for
     * @return the covering season, if any
     */
    @Query("SELECT s FROM RateSeason s "
            + "WHERE s.roomTypeId = :roomTypeId AND s.hotelId = :hotelId "
            + "AND s.startDate <= :date AND s.endDate >= :date")
    Optional<RateSeason> findCovering(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("hotelId") UUID hotelId,
            @Param("date") LocalDate date);
}
