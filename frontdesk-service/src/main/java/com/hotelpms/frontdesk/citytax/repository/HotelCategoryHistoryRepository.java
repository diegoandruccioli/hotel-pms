package com.hotelpms.frontdesk.citytax.repository;

import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link HotelCategoryHistory}.
 */
@Repository
public interface HotelCategoryHistoryRepository extends JpaRepository<HotelCategoryHistory, UUID> {

    /**
     * Lists a hotel's full category history, most recent {@code validFrom} first.
     *
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the history, newest first
     */
    List<HotelCategoryHistory> findAllByHotelIdOrderByValidFromDesc(UUID hotelId);

    /**
     * Resolves the category a hotel held on a given date — at most one row can
     * ever match, guaranteed by {@code excl_hotel_category_no_overlap}.
     *
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @param date    the date to resolve the category for (typically the stay's check-in date)
     * @return the category active on that date, if any is recorded
     */
    @Query("SELECT h FROM HotelCategoryHistory h WHERE h.hotelId = :hotelId "
            + "AND h.validFrom <= :date AND (h.validTo IS NULL OR h.validTo > :date)")
    Optional<HotelCategoryHistory> findApplicableByHotelId(
            @Param("hotelId") UUID hotelId, @Param("date") LocalDate date);

    /**
     * Finds the hotel's currently open-ended category entry (if any) — the row
     * a category change must close by setting its {@code validTo} before
     * inserting the new one.
     *
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the open-ended entry, if any
     */
    Optional<HotelCategoryHistory> findByHotelIdAndValidToIsNull(UUID hotelId);
}
