package com.hotelpms.stay.client;

import com.hotelpms.stay.client.dto.RoomResponse;
import com.hotelpms.stay.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * OpenFeign Client for communicating with the Inventory Service (Rooms).
 */
@FeignClient(name = "inventory-service")
public interface InventoryClient {

    /** Constant for unknown fallback values. */
    String UNKNOWN_VALUE = "UNKNOWN";

    /**
     * Gets a room by its ID.
     *
     * @param id the room ID
     * @return the room response
     */
    @GetMapping("/api/v1/rooms/{id}")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getRoomByIdFallback")
    RoomResponse getRoomById(@PathVariable("id") UUID id);

    /**
     * Updates only the housekeeping status of a room.
     *
     * @param id     the room ID
     * @param status the new status string (e.g., "DIRTY")
     * @return the updated room response
     */
    @PatchMapping("/api/v1/rooms/{id}/status")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "updateRoomStatusFallback")
    RoomResponse updateRoomStatus(@PathVariable("id") UUID id, @RequestBody String status);

    /**
     * Fallback method for getRoomById.
     *
     * @param id        the room ID
     * @param throwable the throwable
     * @return the room response or throws an exception
     */
    default RoomResponse getRoomByIdFallback(final UUID id, final Throwable throwable) {
        return new RoomResponse(id, UNKNOWN_VALUE, UNKNOWN_VALUE);
    }

    /**
     * Fallback method for updateRoomStatus.
     * Always throws so callers can detect the failure and execute compensating transactions.
     *
     * @param id        the room ID
     * @param status    the new status that could not be applied
     * @param throwable the underlying cause
     * @return never — this method always throws
     * @throws ExternalServiceException always, to signal the update did not occur
     */
    default RoomResponse updateRoomStatusFallback(final UUID id, final String status, final Throwable throwable) {
        throw new ExternalServiceException(
                "Inventory service unavailable — room status not updated: " + throwable.getMessage(), throwable);
    }
}
