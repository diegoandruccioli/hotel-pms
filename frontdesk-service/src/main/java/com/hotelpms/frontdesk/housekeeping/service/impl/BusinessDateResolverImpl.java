package com.hotelpms.frontdesk.housekeeping.service.impl;

import com.hotelpms.frontdesk.housekeeping.service.BusinessDateResolver;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Default implementation of {@link BusinessDateResolver}.
 *
 * <p>Takes a {@link Clock} rather than calling {@code ZonedDateTime.now(ZoneId)}
 * directly, solely so a test can pin "now" via {@code Clock.fixed(...)} — see
 * {@code housekeeping.config.ClockConfig} for the production bean ({@code
 * Clock.systemUTC()}). This is the one place in the codebase that does this;
 * it is not a project-wide {@code Clock} migration.
 */
@Service
@RequiredArgsConstructor
public class BusinessDateResolverImpl implements BusinessDateResolver {

    private final HotelSettingsService hotelSettingsService;
    private final Clock clock;

    /** {@inheritDoc} */
    @Override
    public LocalDate resolve(final UUID hotelId) {
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
        final ZonedDateTime nowAtHotel = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of(settings.timezone()));
        return nowAtHotel.getHour() < settings.housekeepingDayCutoffHour()
                ? nowAtHotel.toLocalDate().minusDays(1)
                : nowAtHotel.toLocalDate();
    }
}
