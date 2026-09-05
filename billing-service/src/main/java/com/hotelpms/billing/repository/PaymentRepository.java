package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Sums payment amounts by method for a hotel's payments recorded within a
     * (half-open) time window — the night-audit cash-closing summary.
     * {@code Payment} has no {@code hotelId} of its own; the tenant scope
     * comes from its parent {@code Invoice} (T-OPS style: same pattern as
     * every other cross-entity aggregate query in this repository layer).
     *
     * @param hotelId the hotel to scope to
     * @param from    inclusive lower bound on {@code paymentDate}
     * @param to      exclusive upper bound on {@code paymentDate}
     * @return one row per {@code PaymentMethod} that had at least one payment
     */
    @Query("SELECT p.paymentMethod AS paymentMethod, SUM(p.amount) AS total "
            + "FROM Payment p WHERE p.invoice.hotelId = :hotelId "
            + "AND p.paymentDate >= :from AND p.paymentDate < :to "
            + "GROUP BY p.paymentMethod")
    List<PaymentMethodTotal> sumByHotelIdAndPaymentDateBetweenGroupedByMethod(
            @Param("hotelId") UUID hotelId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
