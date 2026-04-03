package com.hotelpms.reservation.client;

import com.hotelpms.reservation.client.dto.RoomResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * OpenFeign Client for communicating with Inventory Service.
 *
 * <p>
 * A {@link CircuitBreaker} is applied to {@link #getRoomById}.
 * When the circuit is open, {@code getRoomByIdFallback} is
 * invoked and returns a default response, allowing the caller to decide the
 * appropriate business response without propagating infrastructure noise.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@FeignClient(name = "inventory-service", url = "${APPLICATION_CONFIG_INVENTORY_SERVICE_URL:http://inventory-service:8081}")
public interface InventoryClient {

    /** Logger used inside default fallback methods. */
    Logger LOG = LoggerFactory.getLogger(InventoryClient.class);

    /**
     * Gets a room by id.
     *
     * @param id the room id
     * @return an {@link Optional} containing the room response, or empty on failure
     */
    @GetMapping("/api/v1/rooms/{id}")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getRoomByIdFallback")
    Optional<RoomResponse> getRoomById(@PathVariable("id") UUID id);

    /**
     * Fallback method invoked when the Inventory Service is unavailable or retries
     * are exhausted. Returns a default DTO.
     *
     * @param id        the room id that was requested
     * @param throwable the throwable that triggered the fallback
     * @return an Optional representing a degraded, safe default
     */
    default Optional<RoomResponse> getRoomByIdFallback(final UUID id, final Throwable throwable) {
        LOG.warn("[InventoryClient] Fallback triggered for roomId={}: {}", id, throwable.getMessage());
        return Optional
                .of(new RoomResponse(id, "UNKNOWN", null, "UNKNOWN", false, LocalDateTime.now(), LocalDateTime.now()));
    }
}
