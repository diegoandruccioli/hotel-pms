package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Finds an invoice by its ID scoped to a specific hotel (IDOR-safe lookup).
     * Returns empty if the invoice belongs to a different hotel, preventing
     * cross-tenant access via UUID enumeration.
     *
     * @param id      the invoice UUID
     * @param hotelId the hotel UUID from the authenticated request
     * @return the invoice if it belongs to the given hotel
     */
    Optional<Invoice> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Finds all invoices belonging to a specific hotel, paginated (multi-tenancy).
     * Collections (charges, payments) are loaded lazily within the caller's transaction.
     * Using @EntityGraph with two Bag collections causes MultipleBagFetchException.
     *
     * @param hotelId  the hotel UUID
     * @param pageable pagination parameters
     * @return a page of invoices for the given hotel
     */
    Page<Invoice> findByHotelId(UUID hotelId, Pageable pageable);

    /**
     * Finds all invoices belonging to a specific hotel (multi-tenancy).
     *
     * @param hotelId the hotel UUID
     * @return a list of invoices
     */
    List<Invoice> findByHotelId(UUID hotelId);

    /**
     * Finds the most recent invoice for a reservation scoped to a hotel
     * (used during check-out validation).
     *
     * @param reservationId the reservation UUID
     * @param hotelId       the hotel UUID from the authenticated request
     * @return the latest invoice if it belongs to the given hotel
     */
    Optional<Invoice> findFirstByReservationIdAndHotelIdOrderByIssueDateDesc(UUID reservationId, UUID hotelId);

    /**
     * Finds all invoices associated with a specific guest.
     *
     * @param guestId the guest UUID
     * @return a list of invoices
     */
    List<Invoice> findByGuestId(UUID guestId);

    /**
     * Finds all invoices whose issue date falls within the given window.
     * Used by the Owner financial report to aggregate revenues by date range.
     *
     * @param start beginning of the time window (inclusive)
     * @param end   end of the time window (exclusive)
     * @return list of matching invoices
     */
    List<Invoice> findByIssueDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Finds the active invoice for a stay, scoped to a hotel (IDOR-safe lookup).
     * Returns empty if no invoice exists for the given stay and hotel combination.
     * Used to detect duplicates before creating and to look up the invoice for charge additions.
     *
     * @param stayId  the stay UUID
     * @param hotelId the hotel UUID from the authenticated request
     * @return the invoice for the given stay if it belongs to the given hotel
     */
    Optional<Invoice> findByStayIdAndHotelId(UUID stayId, UUID hotelId);

    /**
     * Finds the most recent invoice for a guest within a hotel, ordered by
     * issue date descending.
     * Used by the guest-service GDPR legal-hold guard (T-GST-05) to verify
     * whether the Codice Civile art. 2220 ten-year fiscal retention obligation
     * has expired before anonymising a guest profile.
     *
     * @param guestId the guest UUID
     * @param hotelId the hotel UUID (tenant isolation)
     * @return the most recent invoice if present
     */
    Optional<Invoice> findTopByGuestIdAndHotelIdOrderByIssueDateDesc(UUID guestId, UUID hotelId);

    /**
     * Finds all invoices for a guest within a hotel, ordered by issue date descending.
     * Used by the GDPR Art. 20 data-export endpoint to return full invoice history.
     *
     * @param guestId the guest UUID
     * @param hotelId the hotel UUID (tenant isolation)
     * @return list of invoices, most recent first
     */
    List<Invoice> findByGuestIdAndHotelIdOrderByIssueDateDesc(UUID guestId, UUID hotelId);
}
