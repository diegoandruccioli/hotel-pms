package com.hotelpms.billing.client;

import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Feign client for fetching hotel profile settings from stay-service.
 * Used to populate hotel header data in generated PDF invoices.
 * The {@code X-Auth-Hotel} header is propagated automatically by {@link FeignHeaderConfig}.
 */
@FeignClient(name = "stay-service-settings", url = "${application.config.stay-service-url}")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface HotelSettingsClient {

    /**
     * Returns the settings row for the hotel identified by the {@code X-Auth-Hotel} header.
     *
     * @return hotel settings; falls back to a placeholder if stay-service is unavailable
     */
    @GetMapping("/api/v1/stays/settings")
    @CircuitBreaker(name = "hotelSettingsService", fallbackMethod = "getSettingsFallback")
    HotelSettingsResponse getSettings();

    /**
     * Fallback when stay-service is unreachable.
     * Returns a placeholder so PDF generation degrades gracefully (empty hotel header).
     *
     * @param throwable the exception that triggered the fallback
     * @return a default {@link HotelSettingsResponse} with empty fields
     */
    default HotelSettingsResponse getSettingsFallback(final Throwable throwable) {
        return new HotelSettingsResponse(null, "Hotel", "", "", "", null);
    }
}
