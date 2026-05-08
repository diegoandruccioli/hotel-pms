package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.InvoiceCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for InvoiceCharge entities.
 */
@Repository
public interface InvoiceChargeRepository extends JpaRepository<InvoiceCharge, UUID> {

    /**
     * Finds all charges belonging to a specific invoice.
     *
     * @param invoiceId the invoice UUID
     * @return list of charges for the given invoice
     */
    List<InvoiceCharge> findByInvoiceId(UUID invoiceId);
}
