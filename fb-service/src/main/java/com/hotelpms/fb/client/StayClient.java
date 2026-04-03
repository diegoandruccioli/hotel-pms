package com.hotelpms.fb.client;

import com.hotelpms.fb.client.dto.StayResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * OpenFeign client for communicating with the Stay service.
 */
@FunctionalInterface
@FeignClient(name = "stay-service", url = "${application.config.stay-service-url}")
public interface StayClient {

    /**
     * Retrieves a stay by its ID.
     *
     * @param id the ID of the stay to retrieve
     * @return the stay details
     */
    @GetMapping("/api/stays/{id}")
    @CircuitBreaker(name = "stayService", fallbackMethod = "getStayFallback")
    StayResponse getStayById(@PathVariable("id") UUID id);

    /**
     * Fallback method for getStayById in case the Stay service is unreachable.
     *
     * @param id        the ID of the stay
     * @param throwable the exception that caused the fallback
     * @return a default or empty StayResponse
     */
    default StayResponse getStayFallback(final UUID id, final Throwable throwable) {
        // Return a default response or null depending on business requirements.
        // For F&B, we might need to know if the service is down so we can handle it
        // gracefully.
        return new StayResponse(id, "UNKNOWN");
    }
}
