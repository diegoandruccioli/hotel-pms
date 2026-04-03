package com.hotelpms.stay.client;

import com.hotelpms.stay.client.dto.ReservationResponse;
import com.hotelpms.stay.client.dto.ReservationStatusUpdateRequest;
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
     * Fallback method for getReservationById.
     *
     * @param id        the reservation ID
     * @param throwable the throwable
     * @return the reservation response or throws an exception
     */
    default ReservationResponse getReservationByIdFallback(final UUID id, final Throwable throwable) {
        return new ReservationResponse(id, null, null, "UNKNOWN", null);
    }
}
