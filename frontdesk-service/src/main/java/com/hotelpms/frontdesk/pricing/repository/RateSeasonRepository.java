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

    /**
     * Finds every active season for a room type, scoped to a hotel, whose range
     * overlaps {@code [startDate, endDate]} (both inclusive). Unlike
     * {@link #findCovering}, this can return more than one row — used by the
     * rate calendar's bulk-apply split/trim logic to find every existing season
     * a newly-applied range would collide with.
     *
     * @param roomTypeId the room type UUID
     * @param hotelId    the hotel UUID (multi-tenant scoping)
     * @param startDate  the overlap range start (inclusive)
     * @param endDate    the overlap range end (inclusive)
     * @return the overlapping seasons, oldest first
     */
    @Query("SELECT s FROM RateSeason s "
            + "WHERE s.roomTypeId = :roomTypeId AND s.hotelId = :hotelId "
            + "AND s.startDate <= :endDate AND s.endDate >= :startDate "
            + "ORDER BY s.startDate")
    List<RateSeason> findOverlapping(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("hotelId") UUID hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Finds every active season for a hotel (across all room types) whose range
     * overlaps {@code [startDate, endDate]} (both inclusive) — one query for the
     * whole rate calendar grid, instead of one {@link #findCovering} call per
     * night per room type.
     *
     * @param hotelId   the hotel UUID (multi-tenant scoping)
     * @param startDate the overlap range start (inclusive)
     * @param endDate   the overlap range end (inclusive)
     * @return the overlapping seasons, grouped by room type, oldest first within each
     */
    @Query("SELECT s FROM RateSeason s "
            + "WHERE s.hotelId = :hotelId "
            + "AND s.startDate <= :endDate AND s.endDate >= :startDate "
            + "ORDER BY s.roomTypeId, s.startDate")
    List<RateSeason> findAllByHotelIdAndDateRangeOverlapping(
            @Param("hotelId") UUID hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
