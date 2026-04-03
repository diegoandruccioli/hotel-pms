package com.hotelpms.stay.client;

import com.hotelpms.stay.client.dto.InvoiceStatusResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * OpenFeign Client for communicating with the Billing Service.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@FeignClient(name = "billing-service")
public interface BillingClient {

    /**
     * Retrieves the most recent invoice for a reservation.
     *
     * @param reservationId the reservation UUID
     * @return the invoice status response
     */
    @GetMapping("/api/v1/invoices/reservation/{reservationId}/latest")
    @CircuitBreaker(name = "billingService", fallbackMethod = "getLatestInvoiceFallback")
    InvoiceStatusResponse getLatestInvoiceByReservation(@PathVariable("reservationId") UUID reservationId);

    /**
     * Fallback for getLatestInvoiceByReservation.
     *
     * @param reservationId the reservation id
     * @param throwable     the throwable
     * @return throws exception
     */
    default InvoiceStatusResponse getLatestInvoiceFallback(final UUID reservationId, final Throwable throwable) {
        return new InvoiceStatusResponse(null, reservationId, "UNAVAILABLE", java.math.BigDecimal.ZERO);
    }
}
