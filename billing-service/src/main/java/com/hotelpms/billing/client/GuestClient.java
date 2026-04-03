package com.hotelpms.billing.client;

import com.hotelpms.billing.client.dto.GuestResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for interacting with the guest service.
 */
@FeignClient(name = "guest-service", path = "/api/v1/guests")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface GuestClient {

    /**
     * Fetches guest details by their ID.
     * 
     * @param id the guest UUID
     * @return the GuestResponse
     */
    @GetMapping("/{id}")
    @CircuitBreaker(name = "guestService", fallbackMethod = "getGuestFallback")
    GuestResponse getGuestById(@PathVariable("id") UUID id);

    /**
     * Fallback method if the guest service is unavailable.
     * 
     * @param id        the original guest ID
     * @param throwable the exception that caused the fallback
     * @return a default GuestResponse
     */
    default GuestResponse getGuestFallback(final UUID id, final Throwable throwable) {
        return new GuestResponse(id, "Unknown", "Guest", "unknown@guest.com");
    }
}
