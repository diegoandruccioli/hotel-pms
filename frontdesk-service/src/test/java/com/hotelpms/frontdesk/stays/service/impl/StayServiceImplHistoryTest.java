package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GatewayEventsClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.StaySummaryResponse;
import com.hotelpms.frontdesk.stays.mapper.StayMapper;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StayServiceImpl#getStayHistoryForGuest}: the history row carries the
 * stay's reservation (null for a walk-in) so the guest sheet can link to it. Kept apart from
 * {@code StayServiceImplTest}, which is at the file-length limit.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StayServiceImplHistoryTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.now().minusDays(5);

    @Mock
    private StayRepository stayRepository;

    @Mock
    private StayGuestRepository stayGuestRepository;

    @Mock
    private StayMapper stayMapper;

    @Mock
    private BillingClient billingClient;

    @Mock
    private GuestClient guestClient;

    @Mock
    private ReservationService reservationService;

    @Mock
    private RoomService roomService;

    @Mock
    private AlloggiatiWebSenderService alloggiatiWebSenderService;

    @Mock
    private HotelSettingsService hotelSettingsService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private RatePricingService ratePricingService;

    @Mock
    private CityTaxAssessmentService cityTaxAssessmentService;

    @Mock
    private GatewayEventsClient gatewayEventsClient;

    private StayServiceImpl stayService;

    private UUID guestId;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        guestId = Objects.requireNonNull(UUID.randomUUID());
        hotelId = Objects.requireNonNull(UUID.randomUUID());

        final StayInvoiceResolver stayInvoiceResolver = new StayInvoiceResolver(billingClient);
        stayService = new StayServiceImpl(
                stayRepository, stayGuestRepository, stayMapper, guestClient, roomService, reservationService,
                new StayCheckInValidator(guestClient, reservationService, roomService),
                new StayBillingCoordinator(billingClient, roomService, stayRepository, reservationService,
                        ratePricingService, cityTaxAssessmentService, stayInvoiceResolver),
                new StayAlloggiatiCoordinator(alloggiatiWebSenderService, hotelSettingsService, stayRepository),
                new StayNotificationCoordinator(
                        notificationClient, guestClient, billingClient, hotelSettingsService, stayRepository),
                new StayReservationSync(reservationService, stayRepository),
                gatewayEventsClient,
                cityTaxAssessmentService,
                stayInvoiceResolver);
    }

    private Stay stay(final UUID reservationId) {
        return Stay.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .reservationId(reservationId)
                .guestId(guestId)
                .roomId(UUID.randomUUID())
                .roomNumber("101")
                .status(StayStatus.CHECKED_OUT)
                .actualCheckInTime(CHECK_IN)
                .actualCheckOutTime(CHECK_IN.plusDays(2))
                .expectedCheckOutDate(CHECK_IN.toLocalDate().plusDays(2))
                .build();
    }

    @Test
    void historyRowsCarryTheReservationOfEachStay() {
        final UUID reservationId = UUID.randomUUID();
        final Stay booked = stay(reservationId);
        when(stayRepository.findByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(guestId, hotelId))
                .thenReturn(List.of(booked));

        final List<StaySummaryResponse> history = stayService.getStayHistoryForGuest(guestId, hotelId);

        assertEquals(1, history.size());
        assertEquals(booked.getId(), history.get(0).stayId());
        assertEquals(reservationId, history.get(0).reservationId());
        assertEquals(StayStatus.CHECKED_OUT, history.get(0).status());
    }

    @Test
    void historyRowOfAWalkInHasNoReservation() {
        when(stayRepository.findByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(guestId, hotelId))
                .thenReturn(List.of(stay(null)));

        final List<StaySummaryResponse> history = stayService.getStayHistoryForGuest(guestId, hotelId);

        assertNull(history.get(0).reservationId());
    }
}
