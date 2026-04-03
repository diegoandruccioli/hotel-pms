package com.hotelpms.billing.client;

import com.hotelpms.billing.client.dto.ReservationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Feign client for interacting with the reservation service.
 */
@FeignClient(name = "reservation-service", path = "/api/v1/reservations")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ReservationClient {

    /**
     * Fetches reservation details by its ID.
     * 
     * @param id the reservation UUID
     * @return the ReservationResponse
     */
    @GetMapping("/{id}")
    @CircuitBreaker(name = "reservationService", fallbackMethod = "getReservationFallback")
    ReservationResponse getReservationById(@PathVariable("id") UUID id);

    /**
     * Fallback method if the reservation service is unavailable.
     * 
     * @param id        the original reservation ID
     * @param throwable the exception that caused the fallback
     * @return a default ReservationResponse
     */
    default ReservationResponse getReservationFallback(final UUID id, final Throwable throwable) {
        return new ReservationResponse(id, null, null, LocalDate.now(), LocalDate.now(), BigDecimal.ZERO, "UNKNOWN");
    }
}
