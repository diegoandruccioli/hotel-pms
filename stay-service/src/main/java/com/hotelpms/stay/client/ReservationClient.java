package com.hotelpms.stay.client;

import com.hotelpms.stay.client.dto.ReservationResponse;
import com.hotelpms.stay.client.dto.ReservationStatusUpdateRequest;
import com.hotelpms.stay.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * OpenFeign Client for communicating with the Reservation Service.
 */
@FeignClient(name = "reservation-service")
public interface ReservationClient {

    /**
     * Gets a reservation by its ID.
     *
     * @param id the reservation ID
     * @return the reservation response
     */
    @GetMapping("/api/v1/reservations/{id}")
    @CircuitBreaker(name = "reservationService", fallbackMethod = "getReservationByIdFallback")
    ReservationResponse getReservationById(@PathVariable("id") UUID id);

    /**
     * Updates reservation status and/or actual guests.
     *
     * @param id      the reservation ID
     * @param request the status/guests payload
     */
    @PatchMapping("/api/v1/reservations/{id}/status-and-guests")
    void updateStatusAndGuests(@PathVariable("id") UUID id,
            @RequestBody ReservationStatusUpdateRequest request);

    /**
     * Fallback method for getReservationById — called when the circuit breaker is open
     * or the reservation-service is unreachable. Throws {@link ExternalServiceException}
     * so the caller receives 502 (BAD_GATEWAY) instead of a misleading 409.
     *
     * @param id        the reservation ID
     * @param throwable the cause (CallNotPermittedException or FeignException)
     * @return never returns (always throws {@link ExternalServiceException})
     */
    default ReservationResponse getReservationByIdFallback(final UUID id, final Throwable throwable) {
        throw new ExternalServiceException("RESERVATION_SERVICE_UNAVAILABLE", throwable);
    }
}
