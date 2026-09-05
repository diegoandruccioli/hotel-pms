package com.hotelpms.frontdesk.nightaudit.repository;

import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link NightAuditRun}.
 */
public interface NightAuditRunRepository extends JpaRepository<NightAuditRun, UUID> {

    /**
     * Finds the run for a given hotel and business date, if one exists —
     * regardless of status (COMPLETED or FAILED). Backs both the
     * already-closed guard and the failed-retry path.
     *
     * @param hotelId      the hotel to scope to
     * @param businessDate the business date to look up
     * @return the run, if any
     */
    Optional<NightAuditRun> findByHotelIdAndBusinessDate(UUID hotelId, LocalDate businessDate);

    /**
     * Lists a hotel's runs newest-first, for the night-audit history page.
     *
     * @param hotelId  the hotel to scope to
     * @param pageable pagination and sorting
     * @return a page of runs
     */
    Page<NightAuditRun> findByHotelIdOrderByBusinessDateDesc(UUID hotelId, Pageable pageable);
}
