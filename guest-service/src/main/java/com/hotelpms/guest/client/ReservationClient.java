package com.hotelpms.guest.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for communicating with the Reservation Service.
 */
@FeignClient(name = "reservation-service", url = "${application.config.reservation-service-url:http://localhost:8083}")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ReservationClient {

    /**
     * Checks if a guest has active reservations before deletion.
     * Note: Assume endpoint exists in reservation-service or will be implemented.
     *
     * @param guestId the ID of the guest
     * @return true if there are active reservations
     */
    @GetMapping("/api/v1/reservations/guest/{guestId}/active")
    @CircuitBreaker(name = "reservationService", fallbackMethod = "hasActiveReservationsFallback")
    boolean hasActiveReservations(@PathVariable("guestId") UUID guestId);

    /**
     * Fallback method for hasActiveReservations when Reservation Service is down.
     * Deny deletion by default for safety.
     *
     * @param guestId   the ID of the guest
     * @param throwable the cause of the failure
     * @return true (denying deletion)
     */
    default boolean hasActiveReservationsFallback(final UUID guestId, final Throwable throwable) {
        // Log error normally, returning true essentially blocks guest deletion as a
        // fail-safe
        return true;
    }
}
