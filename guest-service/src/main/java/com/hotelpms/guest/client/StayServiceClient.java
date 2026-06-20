package com.hotelpms.guest.client;

import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import com.hotelpms.guest.client.dto.StaySummaryClientResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for querying stay data from the stays domain in frontdesk-service
 * (formerly stay-service, see ADR-001 in backup/DECISIONS.md).
 * Used exclusively by the GDPR legal-hold guard (T-GST-05) to verify
 * the TULPS five-year retention obligation before anonymising a guest profile.
 */
@FeignClient(name = "frontdesk-service-stays",
        url = "${application.config.frontdesk-service-url:http://localhost:8081}")
public interface StayServiceClient {

    /**
     * Returns the most recent check-in date for a guest within the caller's hotel.
     *
     * @param guestId the guest UUID
     * @return last stay information
     */
    @GetMapping("/api/v1/stays/guest/{guestId}/last-date")
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

    /**
     * Returns all stay summaries for a guest. Used by the GDPR Art. 20 export.
     *
     * @param guestId the guest UUID
     * @return list of stay summaries
     */
    @GetMapping("/api/v1/stays/guest/{guestId}/history")
    @CircuitBreaker(name = "stayService", fallbackMethod = "stayHistoryFallback")
    List<StaySummaryClientResponse> getStayHistory(@PathVariable("guestId") UUID guestId);

    /**
     * Fallback: returns an empty list so the export completes with a partial result.
     *
     * @param guestId   the guest UUID
     * @param throwable the cause of the failure
     * @return empty list
     */
    default List<StaySummaryClientResponse> stayHistoryFallback(
            final UUID guestId, final Throwable throwable) {
        return List.of();
    }
}
