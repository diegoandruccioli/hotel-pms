package com.hotelpms.reservation.client;

import com.hotelpms.reservation.client.dto.GuestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for the Guest Service.
 */
@FeignClient(name = "guest-service", url = "${APPLICATION_CONFIG_GUEST_SERVICE_URL:http://guest-service:8083}")
public interface GuestClient {

    /**
     * Gets a guest by ID.
     *
     * @param id the guest ID
     * @return the guest details
     */
    @GetMapping("/api/v1/guests/{id}")
    GuestResponse getGuestById(@PathVariable("id") UUID id);

    /**
     * Gets a list of guests by their IDs.
     *
     * @param ids the list of guest UUIDs
     * @return the list of guest details
     */
    @PostMapping("/api/v1/guests/batch")
    List<GuestResponse> getGuestsBatch(@RequestBody List<UUID> ids);
}
