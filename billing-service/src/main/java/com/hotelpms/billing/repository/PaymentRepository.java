package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for Payment entities.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Finds all payments associated with a specific invoice.
     * 
     * @param invoiceId the invoice UUID
     * @return a list of payments
     */
    List<Payment> findByInvoiceId(UUID invoiceId);
}
