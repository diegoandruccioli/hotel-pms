package com.hotelpms.frontdesk.stays.repository;

import com.hotelpms.frontdesk.stays.domain.StayGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link StayGuest}. {@code StayGuest} carries no {@code hotel_id}
 * of its own (it's a child of {@code Stay}, scoped via {@code stay.hotelId} in
 * every custom query below) — tenant isolation here is enforced at the query
 * level, the same pattern as {@code CityTaxAssessmentLineRepository}.
 */
public interface StayGuestRepository extends JpaRepository<StayGuest, UUID> {

    /**
     * Every guest whose own arrival date falls on {@code date}, for the given hotel —
     * the correct selection for a daily Alloggiati Web report (Parte 1): the report is
     * due within 24h of each guest's own arrival, not the room's check-in date.
     *
     * @param hotelId the hotel to scope to
     * @param date    the arrival date to select
     * @return matching guests, in no particular order
     */
    @Query("SELECT g FROM StayGuest g WHERE g.stay.hotelId = :hotelId AND g.arrivalDate = :date")
    List<StayGuest> findByHotelIdAndArrivalDate(@Param("hotelId") UUID hotelId, @Param("date") LocalDate date);

    /**
     * Every guest for the given hotel still awaiting resubmission — Alloggiati Web has
     * no rectification API, so a guest corrected after their original send must be
     * picked up by the next report run regardless of their arrival date.
     *
     * @param hotelId the hotel to scope to
     * @return guests flagged {@code needsResubmit}
     */
    @Query("SELECT g FROM StayGuest g WHERE g.stay.hotelId = :hotelId AND g.needsResubmit = true")
    List<StayGuest> findByHotelIdAndNeedsResubmitTrue(@Param("hotelId") UUID hotelId);
}
