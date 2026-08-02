package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.InvoiceFiscalExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for InvoiceFiscalExport entities.
 */
@Repository
public interface InvoiceFiscalExportRepository extends JpaRepository<InvoiceFiscalExport, UUID> {

    /**
     * Checks whether an invoice has ever been fiscally exported — drives the
     * post-export immutability guard on fiscally-relevant invoice fields.
     *
     * @param invoiceId the invoice UUID
     * @return true if at least one export exists for this invoice
     */
    boolean existsByInvoiceId(UUID invoiceId);

    /**
     * Finds all fiscal exports for a hotel within a period, for the batch export
     * endpoint's index — ordered oldest first so the ZIP/CSV index reads chronologically.
     *
     * @param hotelId the hotel UUID
     * @param from    inclusive lower bound on exportedAt
     * @param to      inclusive upper bound on exportedAt
     * @return exports for the hotel in the given period, ordered by exportedAt ascending
     */
    List<InvoiceFiscalExport> findByHotelIdAndExportedAtBetweenOrderByExportedAtAsc(
            UUID hotelId, LocalDateTime from, LocalDateTime to);
}
