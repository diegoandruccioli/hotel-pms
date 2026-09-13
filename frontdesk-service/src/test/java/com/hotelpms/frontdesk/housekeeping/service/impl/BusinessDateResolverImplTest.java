package com.hotelpms.frontdesk.housekeeping.service.impl;

import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BusinessDateResolverImpl} — the "which date is
 * tonight's shift printing for" logic behind the housekeeping worksheet.
 * Each test pins {@code Clock} to a specific UTC instant and asserts the
 * resolved date in the hotel's own timezone, exercising the real
 * implementation end to end (no re-derivation of its logic in the test).
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class BusinessDateResolverImplTest {

    private static final String TIMEZONE = "Europe/Rome";
    private static final int CUTOFF_HOUR = 4;
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate OCT_4 = LocalDate.of(2026, 10, 4);
    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);

    @Mock
    private HotelSettingsService hotelSettingsService;

    @Test
    void beforeCutoffResolvesToYesterday() {
        // 02:00 Europe/Rome on 2026-10-05 (CEST, UTC+2) = 00:00 UTC — cutoff 04:00,
        // still printing for the 4th.
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(settings(TIMEZONE, CUTOFF_HOUR));
        final BusinessDateResolverImpl resolver = resolverAt("2026-10-05T00:00:00Z");

        assertEquals(OCT_4, resolver.resolve(HOTEL_ID));
    }

    @Test
    void afterCutoffResolvesToToday() {
        // 05:00 Europe/Rome on 2026-10-05 (CEST) = 03:00 UTC — past the 04:00 cutoff,
        // the new business day has started.
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(settings(TIMEZONE, CUTOFF_HOUR));
        final BusinessDateResolverImpl resolver = resolverAt("2026-10-05T03:00:00Z");

        assertEquals(OCT_5, resolver.resolve(HOTEL_ID));
    }

    @Test
    void zeroCutoffAlwaysResolvesToToday() {
        // 00:30 Europe/Rome on 2026-10-05 (CEST) = 2026-10-04T22:30Z — cutoff 0
        // means "no grace period", the calendar day is the business day.
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(settings(TIMEZONE, 0));
        final BusinessDateResolverImpl resolver = resolverAt("2026-10-04T22:30:00Z");

        assertEquals(OCT_5, resolver.resolve(HOTEL_ID));
    }

    @Test
    void exactCutoffHourResolvesToToday() {
        // "before cutoff" is strict (<), so 04:00 local (02:00Z) is already today.
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(settings(TIMEZONE, CUTOFF_HOUR));
        final BusinessDateResolverImpl resolver = resolverAt("2026-10-05T02:00:00Z");

        assertEquals(OCT_5, resolver.resolve(HOTEL_ID));
    }

    private BusinessDateResolverImpl resolverAt(final String utcInstant) {
        final Clock fixedClock = Clock.fixed(Instant.parse(utcInstant), ZoneId.of("UTC"));
        return new BusinessDateResolverImpl(hotelSettingsService, fixedClock);
    }

    private static HotelSettingsResponse settings(final String timezone, final int cutoffHour) {
        return new HotelSettingsResponse(
                HOTEL_ID, false, "Test Hotel", null, null, null, null, null, false,
                true, true, null, null, null, null, null, null, null, timezone, cutoffHour);
    }
}
