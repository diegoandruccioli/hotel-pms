package com.hotelpms.guest.client;

import com.hotelpms.guest.client.dto.GuestInvoiceClientResponse;
import com.hotelpms.guest.client.dto.InvoiceSummaryClientResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for querying invoice data from the billing-service.
 * Used exclusively by the GDPR legal-hold guard (T-GST-05) to verify
 * the Codice Civile art. 2220 ten-year fiscal retention obligation
 * before anonymising a guest profile.
 */
@FeignClient(name = "billing-service",
        url = "${application.config.billing-service-url:http://localhost:8085}")
public interface BillingServiceClient {

    /**
     * Returns the most recent invoice date for a guest within the caller's hotel.
     *
     * @param guestId the guest UUID
     * @return last invoice information
     */
    @GetMapping("/api/v1/invoices/guest/{guestId}/last-invoice-date")
    @CircuitBreaker(name = "billingService", fallbackMethod = "lastInvoiceDateFallback")
    GuestInvoiceClientResponse getLastInvoiceDate(@PathVariable("guestId") UUID guestId);

    /**
     * Fail-safe fallback: if the billing-service is unavailable, assume the guest
     * has a recent invoice and block deletion to prevent accidental data loss.
     *
     * @param guestId   the guest UUID
     * @param throwable the cause of the failure
     * @return a response indicating an invoice exists (conservative block)
     */
    default GuestInvoiceClientResponse lastInvoiceDateFallback(
            final UUID guestId, final Throwable throwable) {
        return new GuestInvoiceClientResponse(true, null);
    }

    /**
     * Returns all invoice summaries for a guest. Used by the GDPR Art. 20 export.
     *
     * @param guestId the guest UUID
     * @return list of invoice summaries
     */
    @GetMapping("/api/v1/invoices/guest/{guestId}/history")
    @CircuitBreaker(name = "billingService", fallbackMethod = "invoiceHistoryFallback")
    List<InvoiceSummaryClientResponse> getInvoiceHistory(@PathVariable("guestId") UUID guestId);

    /**
     * Fallback: returns an empty list so the export completes with a partial result.
     *
     * @param guestId   the guest UUID
     * @param throwable the cause of the failure
     * @return empty list
     */
    default List<InvoiceSummaryClientResponse> invoiceHistoryFallback(
            final UUID guestId, final Throwable throwable) {
        return List.of();
    }
}
