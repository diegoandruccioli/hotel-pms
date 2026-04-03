package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for Invoice entities.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /**
     * Finds all invoices associated with a specific reservation, newest first.
     *
     * @param reservationId the reservation UUID
     * @return a list of invoices ordered by issue date descending
     */
    List<Invoice> findByReservationIdOrderByIssueDateDesc(UUID reservationId);

    /**
     * Finds the most recent invoice for a reservation (used during check-out
     * validation).
     *
     * @param reservationId the reservation UUID
     * @return the latest invoice if present
     */
    Optional<Invoice> findFirstByReservationIdOrderByIssueDateDesc(UUID reservationId);

    /**
     * Finds all invoices associated with a specific guest.
     *
     * @param guestId the guest UUID
     * @return a list of invoices
     */
    List<Invoice> findByGuestId(UUID guestId);

    /**
     * Finds all invoices belonging to a specific hotel (multi-tenancy).
     *
     * @param hotelId the hotel UUID
     * @return a list of invoices
     */
    List<Invoice> findByHotelId(UUID hotelId);

    /**
     * Finds all invoices whose issue date falls within the given window.
     * Used by the Owner financial report to aggregate revenues by date range.
     *
     * @param start beginning of the time window (inclusive)
     * @param end   end of the time window (exclusive)
     * @return list of matching invoices
     */
    List<Invoice> findByIssueDateBetween(LocalDateTime start, LocalDateTime end);
}
