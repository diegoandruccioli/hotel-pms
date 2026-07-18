package com.hotelpms.frontdesk.client;

import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.GuestSearchPageResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for the Guest Service.
 *
 * <p>Shared by the {@code reservations} and {@code stays} domains within
 * frontdesk-service — guest-service remains a genuinely external dependency
 * after the inventory/reservation/stay consolidation (ADR-001), so a single
 * client avoids two Feign interfaces competing for the same {@code name}.
 *
 * <p><strong>No circuit breaker on {@link #getGuestById}</strong> (deliberate,
 * not an oversight): the pre-consolidation stay-service client had a fallback
 * that silently returned a placeholder {@code "UNKNOWN"} guest when
 * guest-service was unreachable, masking genuine 404s behind a generic
 * "unavailable" placeholder. That collided with reservation-service's existing
 * explicit {@code FeignException.NotFound} → {@code GUEST_NOT_FOUND} handling.
 * Callers that need fail-safe degradation (e.g. check-in pre-fill) catch
 * {@code feign.FeignException} explicitly at the call site instead.
 *
 * <p>The pre-consolidation stay-service client also declared a second method,
 * {@code getGuestDetailsById}, for Alloggiati Web reporting. It had zero call
 * sites — {@code AlloggiatiReportServiceImpl} builds rows entirely from
 * {@code StayGuest} records captured at check-in, never from guest-service —
 * so it was dropped here rather than carried over as dead code.
 *
 * <p>
 * A {@link CircuitBreaker} is kept on {@link #getGuestsBatch} only: it is a
 * low-stakes list-display lookup, and an empty-list fallback ("Unknown Guest"
 * placeholders in a reservations table) carries no compliance risk.
 */
@FeignClient(name = "guest-service", url = "${APPLICATION_CONFIG_GUEST_SERVICE_URL:http://guest-service:8083}")
public interface GuestClient {

    /** Logger used inside default fallback methods. */
    Logger LOG = LoggerFactory.getLogger(GuestClient.class);

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
    @CircuitBreaker(name = "guestService", fallbackMethod = "getGuestsBatchFallback")
    List<GuestResponse> getGuestsBatch(@RequestBody List<UUID> ids);

    /**
     * Fallback invoked when the Guest Service is unavailable or retries are
     * exhausted. Returns an empty list so the caller can still build a response
     * with "Unknown Guest" placeholders.
     *
     * @param ids       the guest IDs that were requested
     * @param throwable the throwable that triggered the fallback
     * @return an empty list
     */
    default List<GuestResponse> getGuestsBatchFallback(final List<UUID> ids, final Throwable throwable) {
        LOG.warn("[GuestClient] getGuestsBatch fallback triggered for {} ids: {}", ids.size(), throwable.getMessage());
        return List.of();
    }

    /**
     * Free-text guest search, hotel-scoped server-side by guest-service. Used to
     * resolve which guest IDs match a search query typed into the reservation
     * search box (C12) — the reservation itself only carries a guestId, no name.
     *
     * @param query the search term (name, email, city)
     * @param size  the maximum number of matches to consider
     * @return the matching guests (first page only, capped at {@code size})
     */
    @GetMapping("/api/v1/guests/search")
    @CircuitBreaker(name = "guestService", fallbackMethod = "searchGuestsFallback")
    GuestSearchPageResponse searchGuests(@RequestParam("query") String query, @RequestParam("size") int size);

    /**
     * Fallback for {@link #searchGuests(String, int)} if guest-service is unavailable:
     * no matches, rather than failing the whole reservation search.
     *
     * @param query     the original search term
     * @param size      the original page size
     * @param throwable the exception that caused the fallback
     * @return an empty result
     */
    default GuestSearchPageResponse searchGuestsFallback(
            final String query, final int size, final Throwable throwable) {
        LOG.warn("[GuestClient] searchGuests fallback triggered for query \"{}\": {}", query, throwable.getMessage());
        return new GuestSearchPageResponse(List.of());
    }
}
