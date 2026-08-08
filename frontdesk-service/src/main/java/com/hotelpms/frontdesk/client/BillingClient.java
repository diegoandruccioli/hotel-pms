package com.hotelpms.frontdesk.client;

import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceForEmailResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
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
     * Logger for fallback diagnostics — interfaces cannot use {@code @Slf4j}.
     */
    Logger LOG = LoggerFactory.getLogger(BillingClient.class);

    /**
     * Status placeholder returned by fallback methods when billing-service is unreachable.
     */
    String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /** Lower bound (inclusive) of the HTTP client-error range. */
    int CLIENT_ERROR_MIN = 400;

    /** Upper bound (exclusive) of the HTTP client-error range. */
    int CLIENT_ERROR_MAX = 500;

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
     * Retrieves the invoice PDF bytes for use as a checkout email attachment.
     * The attachment is optional — a failure here must never block the checkout
     * email itself, so the fallback returns {@code null} instead of propagating.
     *
     * @param invoiceId the invoice UUID
     * @return the PDF bytes, or {@code null} when the circuit is open or the call fails
     */
    @GetMapping(value = "/api/v1/invoices/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @CircuitBreaker(name = CB_BILLING_SERVICE, fallbackMethod = "getInvoicePdfFallback")
    byte[] getInvoicePdf(@PathVariable("invoiceId") UUID invoiceId);

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
     * Adds a charge to the open invoice for a stay (e.g. the room rate at check-in).
     *
     * @param stayId  the stay UUID
     * @param request the charge details
     * @return the created charge response, or {@code null} when the fallback fires
     */
    @PostMapping("/api/v1/invoices/stay/{stayId}/charges")
    @CircuitBreaker(name = CB_BILLING_SERVICE, fallbackMethod = "addChargeFallback")
    ChargeResponse addCharge(@PathVariable("stayId") UUID stayId, @RequestBody ChargeRequest request);

    /**
     * Fallback for getLatestInvoiceByReservation.
     *
     * @param reservationId the reservation id
     * @param throwable     the throwable
     * @return a degraded response with status UNAVAILABLE
     */
    default InvoiceStatusResponse getLatestInvoiceFallback(final UUID reservationId, final Throwable throwable) {
        LOG.warn("[BillingClient] getLatestInvoiceByReservation fallback | reservationId={} | cause={}: {}",
                reservationId, throwable.getClass().getSimpleName(), throwable.getMessage());
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
        LOG.warn("[BillingClient] getInvoiceById fallback | invoiceId={} | cause={}: {}",
                invoiceId, throwable.getClass().getSimpleName(), throwable.getMessage());
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
        LOG.warn("[BillingClient] getInvoiceForEmail fallback | invoiceId={} | cause={}: {}",
                invoiceId, throwable.getClass().getSimpleName(), throwable.getMessage());
        return new InvoiceForEmailResponse(invoiceId, null, null, STATUS_UNAVAILABLE,
                java.math.BigDecimal.ZERO, "EUR", Collections.emptyList());
    }

    /**
     * Fallback for getInvoicePdf — the attachment is optional, so this never
     * throws; the caller sends the checkout email without an attachment.
     *
     * @param invoiceId the invoice id
     * @param throwable the cause
     * @return null
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    default byte[] getInvoicePdfFallback(final UUID invoiceId, final Throwable throwable) {
        LOG.warn("[BillingClient] getInvoicePdf fallback | invoiceId={} | cause={}: {}",
                invoiceId, throwable.getClass().getSimpleName(), throwable.getMessage());
        return null;
    }

    /**
     * Fallback for createInvoiceForStay — returns {@code null} so check-in completes
     * even when billing-service is unavailable.
     *
     * <p>Round 1 bug #1: this fallback fires for <em>any</em> exception, including a
     * legitimate 4xx rejection from billing-service (e.g. a stale-retry race on
     * INVOICE_ALREADY_EXISTS_FOR_STAY), not just real unavailability — rethrow those
     * instead of swallowing them as {@code null}; {@code openInvoiceForStay} in
     * {@code StayServiceImpl} catches them and records the real reason via
     * {@code markInvoiceFlowFailed}, without blocking check-in itself
     * (backup/DECISIONS.md §2.2).
     *
     * @param request   the original request
     * @param throwable the cause
     * @return null, only when the cause is genuine unavailability
     */
    default InvoiceCreatedResponse createInvoiceForStayFallback(
            final StayInvoiceRequest request, final Throwable throwable) {
        LOG.error("[BillingClient] createInvoiceForStay fallback | stayId={} | cause={}: {}",
                request.stayId(), throwable.getClass().getSimpleName(), throwable.getMessage());
        if (throwable instanceof FeignException fe && fe.status() >= CLIENT_ERROR_MIN && fe.status() < CLIENT_ERROR_MAX) {
            throw fe;
        }
        return null;
    }

    /**
     * Fallback for addCharge — returns {@code null} so the caller can mark the stay's
     * invoice flow as failed and offer a retry, instead of losing the failure silently.
     *
     * <p>Round 1 bug #1: same distinction as {@link #createInvoiceForStayFallback} —
     * a legitimate 4xx (e.g. 409 INVOICE_LOCKED_AFTER_EXPORT) is rethrown rather than
     * absorbed as mere unavailability.
     *
     * @param stayId    the stay UUID
     * @param request   the original charge request
     * @param throwable the cause
     * @return null, only when the cause is genuine unavailability
     */
    default ChargeResponse addChargeFallback(
            final UUID stayId, final ChargeRequest request, final Throwable throwable) {
        LOG.error("[BillingClient] addCharge fallback | stayId={} | type={} | cause={}: {}",
                stayId, request.type(), throwable.getClass().getSimpleName(), throwable.getMessage());
        if (throwable instanceof FeignException fe && fe.status() >= CLIENT_ERROR_MIN && fe.status() < CLIENT_ERROR_MAX) {
            throw fe;
        }
        return null;
    }
}
