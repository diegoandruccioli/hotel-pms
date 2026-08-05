package com.hotelpms.fb.client;

import com.hotelpms.fb.client.dto.ChargeRequest;
import com.hotelpms.fb.client.dto.ChargeResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * OpenFeign client for communicating with the Billing Service.
 * Adds F&amp;B charges to an open stay invoice.
 */
@FunctionalInterface
@FeignClient(name = "billing-service", url = "${application.config.billing-service-url}")
public interface BillingClient {

    /** Lower bound (inclusive) of the HTTP client-error range. */
    int CLIENT_ERROR_MIN = 400;

    /** Upper bound (exclusive) of the HTTP client-error range. */
    int CLIENT_ERROR_MAX = 500;

    /**
     * Adds a charge to the open invoice for the given stay.
     *
     * @param stayId  the stay UUID
     * @param request the charge details
     * @return the created charge response, or {@code null} when the fallback fires
     */
    @PostMapping("/api/v1/invoices/stay/{stayId}/charges")
    @CircuitBreaker(name = "billingService", fallbackMethod = "addChargeFallback")
    ChargeResponse addCharge(@PathVariable("stayId") UUID stayId, @RequestBody ChargeRequest request);

    /**
     * Fallback for addCharge.
     *
     * <p>Round 1 bug #1: the resilience4j {@code @CircuitBreaker} annotation invokes
     * this fallback for <em>any</em> exception the call throws — including a
     * legitimate 4xx rejection from billing-service (e.g. 409
     * INVOICE_LOCKED_AFTER_EXPORT), not just real unavailability.
     * {@code resilience4j.circuitbreaker.instances.billingService.ignoreExceptions}
     * (config-service) keeps a 4xx out of the circuit breaker's own failure-rate
     * accounting, but does <strong>not</strong> stop the fallback method itself from
     * being invoked — that is a separate mechanism, verified empirically via the
     * {@code resilience4j_circuitbreaker_calls_seconds_count{kind="ignored"}} metric
     * still going up while the fallback still fired. So the distinction has to be
     * made here: rethrow a real 4xx rejection instead of swallowing it as {@code null}
     * — only true unavailability (5xx/timeout/connection failure) degrades silently.
     *
     * @param stayId    the stay UUID
     * @param request   the original charge request
     * @param throwable the cause
     * @return null, only when the cause is genuine unavailability
     */
    default ChargeResponse addChargeFallback(
            final UUID stayId, final ChargeRequest request, final Throwable throwable) {
        if (throwable instanceof FeignException fe && fe.status() >= CLIENT_ERROR_MIN && fe.status() < CLIENT_ERROR_MAX) {
            throw fe;
        }
        return null;
    }
}
