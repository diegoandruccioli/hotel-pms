package com.hotelpms.frontdesk.pricing.service.impl;

import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatePricingServiceImplTest {

    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(100);
    private static final BigDecimal SEASON_PRICE = BigDecimal.valueOf(180);

    @Mock
    private RateSeasonRepository rateSeasonRepository;

    @Mock
    private RoomTypeService roomTypeService;

    @InjectMocks
    private RatePricingServiceImpl ratePricingService;

    private UUID roomTypeId;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        roomTypeId = Objects.requireNonNull(UUID.randomUUID());
        hotelId = Objects.requireNonNull(UUID.randomUUID());
    }

    private RoomTypeResponse roomTypeWithBasePrice() {
        return new RoomTypeResponse(roomTypeId, "Standard", null, 2, BASE_PRICE, true, null, null);
    }

    private RateSeason season(final LocalDate start, final LocalDate end, final BigDecimal price) {
        return RateSeason.builder()
                .id(Objects.requireNonNull(UUID.randomUUID()))
                .hotelId(hotelId)
                .roomTypeId(roomTypeId)
                .startDate(start)
                .endDate(end)
                .nightlyPrice(price)
                .build();
    }

    @Test
    void resolveNightlyRateReturnsSeasonPriceWhenADateIsCovered() {
        final LocalDate date = LocalDate.of(2026, 8, 10);
        final RateSeason highSeason = season(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), SEASON_PRICE);
        when(rateSeasonRepository.findCovering(roomTypeId, hotelId, date)).thenReturn(Optional.of(highSeason));

        final NightlyRate result = ratePricingService.resolveNightlyRate(roomTypeId, hotelId, date);

        assertEquals(SEASON_PRICE, result.nightlyPrice());
        assertEquals(highSeason.getId(), result.rateSeasonId());
        verifyNoInteractions(roomTypeService);
    }

    @Test
    void resolveNightlyRateFallsBackToBasePriceWhenNoSeasonCoversTheDate() {
        final LocalDate date = LocalDate.of(2026, 3, 5);
        when(rateSeasonRepository.findCovering(roomTypeId, hotelId, date)).thenReturn(Optional.empty());
        when(roomTypeService.getRoomTypeById(roomTypeId, hotelId)).thenReturn(roomTypeWithBasePrice());

        final NightlyRate result = ratePricingService.resolveNightlyRate(roomTypeId, hotelId, date);

        assertEquals(BASE_PRICE, result.nightlyPrice());
        assertNull(result.rateSeasonId());
    }

    @Test
    void resolveStayRatesReturnsOneRatePerNightEntirelyWithinASeason() {
        final LocalDate checkIn = LocalDate.of(2026, 8, 10);
        final LocalDate checkOutExclusive = LocalDate.of(2026, 8, 13);
        final RateSeason highSeason = season(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), SEASON_PRICE);
        when(rateSeasonRepository.findCovering(eq(roomTypeId), eq(hotelId), any(LocalDate.class)))
                .thenReturn(Optional.of(highSeason));

        final List<NightlyRate> rates = ratePricingService.resolveStayRates(roomTypeId, hotelId, checkIn, checkOutExclusive);

        assertEquals(3, rates.size());
        rates.forEach(rate -> assertEquals(SEASON_PRICE, rate.nightlyPrice()));
        assertEquals(checkIn, rates.get(0).date());
        assertEquals(checkOutExclusive.minusDays(1), rates.get(rates.size() - 1).date());
    }

    /**
     * A stay crossing a rate-season boundary: the first night falls inside an
     * active season, the following nights fall outside it (and outside any
     * other season) and must fall back to {@code RoomType.basePrice} — this is
     * exactly the case {@code StayBillingCoordinator.uniformRate} treats as
     * non-uniform (no single per-night price to report as metadata), and the
     * one the booking↔invoice reconciliation fix depends on resolving
     * per-night rather than as a single flat rate.
     */
    @Test
    void resolveStayRatesAcrossASeasonBoundaryMixesSeasonAndBasePrices() {
        final LocalDate checkIn = LocalDate.of(2026, 8, 30);
        final LocalDate dayAfterSeasonEnds = checkIn.plusDays(1);
        final LocalDate twoDaysAfterSeasonEnds = checkIn.plusDays(2);
        final LocalDate checkOutExclusive = checkIn.plusDays(3);
        final RateSeason augustSeason = season(checkIn.withDayOfMonth(1), checkIn, SEASON_PRICE);

        when(rateSeasonRepository.findCovering(roomTypeId, hotelId, checkIn))
                .thenReturn(Optional.of(augustSeason));
        when(rateSeasonRepository.findCovering(roomTypeId, hotelId, dayAfterSeasonEnds))
                .thenReturn(Optional.empty());
        when(rateSeasonRepository.findCovering(roomTypeId, hotelId, twoDaysAfterSeasonEnds))
                .thenReturn(Optional.empty());
        when(roomTypeService.getRoomTypeById(roomTypeId, hotelId)).thenReturn(roomTypeWithBasePrice());

        final List<NightlyRate> rates = ratePricingService.resolveStayRates(roomTypeId, hotelId, checkIn, checkOutExclusive);

        assertEquals(3, rates.size());
        assertEquals(SEASON_PRICE, rates.get(0).nightlyPrice());
        assertEquals(augustSeason.getId(), rates.get(0).rateSeasonId());
        assertEquals(BASE_PRICE, rates.get(1).nightlyPrice());
        assertNull(rates.get(1).rateSeasonId());
        assertEquals(BASE_PRICE, rates.get(2).nightlyPrice());
        assertNull(rates.get(2).rateSeasonId());
    }

    @Test
    void resolveStayRatesReturnsEmptyWhenCheckOutIsNotAfterCheckIn() {
        final LocalDate sameDay = LocalDate.of(2026, 8, 10);

        final List<NightlyRate> rates = ratePricingService.resolveStayRates(roomTypeId, hotelId, sameDay, sameDay);

        assertEquals(List.of(), rates);
        verifyNoInteractions(rateSeasonRepository, roomTypeService);
    }

    @Test
    void resolveNightlyRateRejectsNullRoomTypeId() {
        assertThrows(NullPointerException.class,
                () -> ratePricingService.resolveNightlyRate(null, hotelId, LocalDate.now()));
    }

    @Test
    void resolveNightlyRateRejectsNullHotelId() {
        assertThrows(NullPointerException.class,
                () -> ratePricingService.resolveNightlyRate(roomTypeId, null, LocalDate.now()));
    }

    @Test
    void resolveNightlyRateRejectsNullDate() {
        assertThrows(NullPointerException.class,
                () -> ratePricingService.resolveNightlyRate(roomTypeId, hotelId, null));
    }

    @Test
    void resolveStayRatesRejectsNullCheckIn() {
        assertThrows(NullPointerException.class,
                () -> ratePricingService.resolveStayRates(roomTypeId, hotelId, null, LocalDate.now()));
    }

    @Test
    void resolveStayRatesRejectsNullCheckOut() {
        assertThrows(NullPointerException.class,
                () -> ratePricingService.resolveStayRates(roomTypeId, hotelId, LocalDate.now(), null));
    }
}
