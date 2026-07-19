package com.hotelpms.guest.client;

import com.hotelpms.guest.client.dto.AlloggiatiComuneClientResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for validating a Comune/Provincia pair against the Alloggiati Web
 * reference data owned by frontdesk-service (single source of truth, already used
 * for police check-in reporting) — reused here (P0-1) so a guest's structured
 * address can never carry a Comune/Provincia pair that doesn't actually exist.
 */
@FeignClient(name = "frontdesk-service-lookup",
        url = "${application.config.frontdesk-service-url:http://localhost:8081}")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AlloggiatiComuniClient {

    /**
     * Searches active comuni matching the given name within the given province.
     *
     * @param q         the comune name to search (exact match expected by the caller)
     * @param provincia the 2-character province code
     * @return matching active comuni; empty if none match or frontdesk-service is unreachable
     */
    @GetMapping("/api/v1/stays/lookup/comuni")
    @CircuitBreaker(name = "frontdeskLookupService", fallbackMethod = "searchComuniFallback")
    List<AlloggiatiComuneClientResponse> searchComuni(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "provincia") String provincia);

    /**
     * Fallback when frontdesk-service is unreachable: fails closed (returns no match),
     * matching this codebase's existing conservative-fallback convention for anything
     * cheaper to re-verify than to risk building an invalid FatturaPA {@code Sede} from.
     *
     * @param q         the comune name that was being searched
     * @param provincia the province code that was being searched
     * @param throwable the cause of the failure
     * @return an empty list
     */
    default List<AlloggiatiComuneClientResponse> searchComuniFallback(
            final String q, final String provincia, final Throwable throwable) {
        return List.of();
    }
}
