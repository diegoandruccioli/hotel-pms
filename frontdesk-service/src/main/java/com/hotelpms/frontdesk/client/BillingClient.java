package com.hotelpms.frontdesk.client;

import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceForEmailResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.UUID;

/**
 * OpenFeign Client for communicating with the Billing Service.
 */
@FeignClient(name = "billing-service")
public interface BillingClient {

    /**
     * Status placeholder returned by fallback methods when billing-service is unreachable.
     */
    String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /**
     * Resilience4j circuit breaker instance name shared by all methods of this client.
     */
    String CB_BILLING_SERVICE = "billingService";

    /**
     * Retrieves the most recent invoice for a reservation.
     *
     * @param reservationId the reservation UUID
     * @return the invoice status response
     */
    @GetMapping("/api/v1/invoices/reservation/{reservationId}/latest")
    @CircuitBreaker(name = CB_BILLING_SERVICE, fallbackMethod = "getLatestInvoiceFallback")
    InvoiceStatusResponse getLatestInvoiceByReservation(@PathVariable("reservationId") UUID reservationId);

    /**
     * Retrieves an invoice by its own ID. Used during check-out for walk-in stays
     * (no {@code reservationId}), which can only be looked up via the
     * {@code invoiceId} stored on the {@code Stay} at check-in time.
     *
     * @param invoiceId the invoice UUID
     * @return the invoice status response
     */
    @GetMapping("/api/v1/invoices/{invoiceId}")
    @CircuitBreaker(name = CB_BILLING_SERVICE, fallbackMethod = "getInvoiceByIdFallback")
    InvoiceStatusResponse getInvoiceById(@PathVariable("invoiceId") UUID invoiceId);

    /**
     * Retrieves an invoice with full charge lines for use in checkout summary emails.
     * Calls the same endpoint as {@link #getInvoiceById} but deserialises the richer
     * {@link InvoiceForEmailResponse} projection (Jackson ignores unknown fields).
     *
     * @param invoiceId the invoice UUID
     * @return invoice with charge lines, or a degraded response when the circuit is open
     */
    @GetMapping("/api/v1/invoices/{invoiceId}")
    @CircuitBreaker(name = CB_BILLING_SERVICE, fallbackMethod = "getInvoiceForEmailFallback")
    InvoiceForEmailResponse getInvoiceForEmail(@PathVariable("invoiceId") UUID invoiceId);

    /**
     * Creates an invoice folio in billing-service at check-in.
     *
     * @param request the stay invoice request
     * @return the created invoice response, or {@code null} when the fallback fires
     */
    @PostMapping("/api/v1/invoices/stay")
    @CircuitBreaker(name = CB_BILLING_SERVICE, fallbackMethod = "createInvoiceForStayFallback")
    InvoiceCreatedResponse createInvoiceForStay(@RequestBody StayInvoiceRequest request);

    /**
     * Fallback for getLatestInvoiceByReservation.
     *
     * @param reservationId the reservation id
     * @param throwable     the throwable
     * @return a degraded response with status UNAVAILABLE
     */
    default InvoiceStatusResponse getLatestInvoiceFallback(final UUID reservationId, final Throwable throwable) {
        return new InvoiceStatusResponse(null, reservationId, STATUS_UNAVAILABLE, java.math.BigDecimal.ZERO);
    }

    /**
     * Fallback for getInvoiceById.
     *
     * @param invoiceId the invoice id
     * @param throwable the throwable
     * @return a degraded response with status UNAVAILABLE
     */
    default InvoiceStatusResponse getInvoiceByIdFallback(final UUID invoiceId, final Throwable throwable) {
        return new InvoiceStatusResponse(invoiceId, null, STATUS_UNAVAILABLE, java.math.BigDecimal.ZERO);
    }

    /**
     * Fallback for getInvoiceForEmail — returns a minimal response so checkout email
     * can still be sent without charge line detail.
     *
     * @param invoiceId the invoice id
     * @param throwable the cause
     * @return degraded response with no charges
     */
    default InvoiceForEmailResponse getInvoiceForEmailFallback(
            final UUID invoiceId, final Throwable throwable) {
        return new InvoiceForEmailResponse(invoiceId, null, null, STATUS_UNAVAILABLE,
                java.math.BigDecimal.ZERO, "EUR", Collections.emptyList());
    }

    /**
     * Fallback for createInvoiceForStay — returns {@code null} so check-in completes
     * even when billing-service is unavailable.
     *
     * @param request   the original request
     * @param throwable the cause
     * @return null
     */
    default InvoiceCreatedResponse createInvoiceForStayFallback(
            final StayInvoiceRequest request, final Throwable throwable) {
        return null;
    }
}
