package com.hotelpms.stay.client;

import com.hotelpms.stay.client.dto.AlloggiatiGuestResponse;
import com.hotelpms.stay.client.dto.GuestResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * OpenFeign Client for communicating with the Guest Service.
 */
@FeignClient(name = "guest-service")
public interface GuestClient {

    /** Constant for unknown fallback values. */
    String UNKNOWN_VALUE = "UNKNOWN";

    /**
     * Gets a guest by their ID (slim DTO, used for check-in validation).
     *
     * @param id the guest ID
     * @return the guest response
     */
    @GetMapping("/api/v1/guests/{id}")
    @CircuitBreaker(name = "guestService", fallbackMethod = "getGuestByIdFallback")
    GuestResponse getGuestById(@PathVariable("id") UUID id);

    /**
     * Gets full guest details by ID, including identity documents and DOB.
     * Used by the Alloggiati Web report service.
     *
     * @param id the guest ID
     * @return the enriched Alloggiati guest response
     */
    @GetMapping("/api/v1/guests/{id}")
    @CircuitBreaker(name = "guestService", fallbackMethod = "getGuestDetailsByIdFallback")
    AlloggiatiGuestResponse getGuestDetailsById(@PathVariable("id") UUID id);

    /**
     * Fallback for getGuestById.
     *
     * @param id        the guest ID
     * @param throwable the throwable
     * @return a default GuestResponse
     */
    default GuestResponse getGuestByIdFallback(final UUID id, final Throwable throwable) {
        return new GuestResponse(id, UNKNOWN_VALUE, UNKNOWN_VALUE, "unknown@guest.com");
    }

    /**
     * Fallback for getGuestDetailsById.
     *
     * @param id        the guest ID
     * @param throwable the throwable
     * @return a default AlloggiatiGuestResponse
     */
    default AlloggiatiGuestResponse getGuestDetailsByIdFallback(final UUID id, final Throwable throwable) {
        return new AlloggiatiGuestResponse(id, UNKNOWN_VALUE, UNKNOWN_VALUE, java.time.LocalDate.now(),
                java.util.List.of());
    }
}
