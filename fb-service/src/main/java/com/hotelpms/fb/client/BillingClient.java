package com.hotelpms.fb.client;

import com.hotelpms.fb.client.dto.ChargeRequest;
import com.hotelpms.fb.client.dto.ChargeResponse;
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
     * Fallback for addCharge — returns {@code null} so order confirmation still succeeds
     * when billing-service is temporarily unavailable.
     *
     * @param stayId    the stay UUID
     * @param request   the original charge request
     * @param throwable the cause
     * @return null
     */
    default ChargeResponse addChargeFallback(
            final UUID stayId, final ChargeRequest request, final Throwable throwable) {
        return null;
    }
}
