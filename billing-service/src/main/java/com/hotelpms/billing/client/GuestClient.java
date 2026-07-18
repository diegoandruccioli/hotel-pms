package com.hotelpms.billing.client;

import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.GuestSearchPageResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for interacting with the guest service.
 */
@FeignClient(name = "guest-service", path = "/api/v1/guests")
public interface GuestClient {

    /**
     * Fetches guest details by their ID.
     *
     * @param id the guest UUID
     * @return the GuestResponse
     */
    @GetMapping("/{id}")
    @CircuitBreaker(name = "guestService", fallbackMethod = "getGuestFallback")
    GuestResponse getGuestById(@PathVariable("id") UUID id);

    /**
     * Fallback method if the guest service is unavailable.
     *
     * @param id        the original guest ID
     * @param throwable the exception that caused the fallback
     * @return a default GuestResponse
     */
    default GuestResponse getGuestFallback(final UUID id, final Throwable throwable) {
        return new GuestResponse(id, "Unknown", "Guest", "unknown@guest.com", null, null, null, null, null);
    }

    /**
     * Free-text guest search, hotel-scoped server-side by guest-service. Used to
     * resolve which guest IDs match a search query typed into the invoice search box
     * (C12) — the invoice itself only carries a guestId, no name/email.
     *
     * @param query the search term (name, email, city)
     * @param size  the maximum number of matches to consider
     * @return the matching guests (first page only, capped at {@code size})
     */
    @GetMapping("/search")
    @CircuitBreaker(name = "guestService", fallbackMethod = "searchGuestsFallback")
    GuestSearchPageResponse searchGuests(@RequestParam("query") String query, @RequestParam("size") int size);

    /**
     * Fallback for {@link #searchGuests(String, int)} if guest-service is unavailable:
     * no matches, rather than failing the whole invoice search.
     *
     * @param query     the original search term
     * @param size      the original page size
     * @param throwable the exception that caused the fallback
     * @return an empty result
     */
    default GuestSearchPageResponse searchGuestsFallback(
            final String query, final int size, final Throwable throwable) {
        return new GuestSearchPageResponse(List.of());
    }

    /**
     * Batch-fetches guest details by ID, used to resolve display names for a page of
     * invoice search results in a single round-trip instead of one call per invoice.
     *
     * @param ids the guest UUIDs to resolve
     * @return the matching guests belonging to the caller's hotel (others silently excluded)
     */
    @PostMapping("/batch")
    @CircuitBreaker(name = "guestService", fallbackMethod = "getGuestsBatchFallback")
    List<GuestResponse> getGuestsBatch(@RequestBody List<UUID> ids);

    /**
     * Fallback for {@link #getGuestsBatch(List)}: no names resolved rather than
     * failing the whole invoice search — the UI simply omits the guest name.
     *
     * @param ids       the original guest UUIDs
     * @param throwable the exception that caused the fallback
     * @return an empty list
     */
    default List<GuestResponse> getGuestsBatchFallback(final List<UUID> ids, final Throwable throwable) {
        return List.of();
    }
}
