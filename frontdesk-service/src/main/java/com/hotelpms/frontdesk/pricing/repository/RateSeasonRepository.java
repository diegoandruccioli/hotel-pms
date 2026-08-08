package com.hotelpms.frontdesk.pricing.repository;

import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * Lists every active season for a room type, scoped to a hotel, oldest first.
     *
     * @param roomTypeId the room type UUID
     * @param hotelId    the hotel UUID (multi-tenant scoping)
     * @return the seasons in start-date order
     */
    List<RateSeason> findAllByRoomTypeIdAndHotelIdOrderByStartDateAsc(UUID roomTypeId, UUID hotelId);

    /**
     * Finds a season by id, scoped to a hotel — a season belonging to another
     * hotel must be invisible (T-ROOM-02 pattern), not just filtered from lists.
     *
     * @param id      the season UUID
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the season, if it exists and belongs to this hotel
     */
    Optional<RateSeason> findByIdAndHotelId(UUID id, UUID hotelId);
}
