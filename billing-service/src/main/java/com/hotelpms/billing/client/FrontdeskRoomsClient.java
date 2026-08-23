package com.hotelpms.billing.client;

import com.hotelpms.billing.client.dto.OccupancySummaryResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Feign client for the room-occupancy trend summary in frontdesk-service.
 * Used by the KPI report (epic C4) to compute RevPAR/ADR/Occupancy without
 * billing-service ever handling room/stay data directly. Same target and
 * {@code X-Auth-Hotel} propagation as {@link HotelSettingsClient}.
 */
@FeignClient(name = "frontdesk-service-rooms", url = "${application.config.frontdesk-service-url}")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface FrontdeskRoomsClient {

    /**
     * Returns the room count and bucketed occupied-room-nights trend for a
     * date range, scoped to the hotel identified by the {@code X-Auth-Hotel} header.
     *
     * @param dateFrom    start of the window (inclusive)
     * @param dateTo      end of the window (exclusive)
     * @param granularity the time-bucket size, uppercase enum name (e.g. {@code "WEEK"})
     * @return the occupancy summary; falls back to zero rooms/no periods if frontdesk-service is unavailable
     */
    @GetMapping("/api/v1/frontdesk/occupancy-summary")
    @CircuitBreaker(name = "frontdeskOccupancy", fallbackMethod = "getOccupancySummaryFallback")
    OccupancySummaryResponse getOccupancySummary(
            @RequestParam("dateFrom") LocalDate dateFrom,
            @RequestParam("dateTo") LocalDate dateTo,
            @RequestParam("granularity") String granularity);

    /**
     * Fallback when frontdesk-service is unreachable — the KPI report still
     * renders with zero occupancy/revenue-per-room figures rather than
     * failing outright; revenue-only figures remain accurate.
     *
     * @param dateFrom    original argument
     * @param dateTo      original argument
     * @param granularity original argument
     * @param throwable   the exception that triggered the fallback
     * @return an empty occupancy summary
     */
    default OccupancySummaryResponse getOccupancySummaryFallback(
            final LocalDate dateFrom, final LocalDate dateTo, final String granularity, final Throwable throwable) {
        return new OccupancySummaryResponse(0, List.of());
    }
}
