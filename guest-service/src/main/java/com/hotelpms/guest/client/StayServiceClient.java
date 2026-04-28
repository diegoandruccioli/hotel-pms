package com.hotelpms.guest.client;

import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for querying stay data from the stay-service.
 * Used exclusively by the GDPR legal-hold guard (T-GST-05) to verify
 * the TULPS five-year retention obligation before anonymising a guest profile.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@FeignClient(name = "stay-service",
        url = "${application.config.stay-service-url:http://localhost:8084}")
public interface StayServiceClient {

    /**
     * Returns the most recent check-in date for a guest within the caller's hotel.
     *
     * @param guestId the guest UUID
     * @return last stay information
     */
    @GetMapping("/api/v1/stays/guest/{guestId}/last-stay-date")
    @CircuitBreaker(name = "stayService", fallbackMethod = "lastStayDateFallback")
    GuestLastStayClientResponse getLastStayDate(@PathVariable("guestId") UUID guestId);

    /**
     * Fail-safe fallback: if the stay-service is unavailable, assume the guest
     * has a recent stay and block deletion to prevent accidental data loss.
     *
     * @param guestId   the guest UUID
     * @param throwable the cause of the failure
     * @return a response indicating a stay exists (conservative block)
     */
    default GuestLastStayClientResponse lastStayDateFallback(
            final UUID guestId, final Throwable throwable) {
        return new GuestLastStayClientResponse(true, null);
    }
}
