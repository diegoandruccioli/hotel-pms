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
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.mapper.StayMapper;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the departure-day and Alloggiati-failure overload of
 * {@link StayServiceImpl#getAllStays}. Kept apart from {@code StayServiceImplTest},
 * which is at the file-length limit.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StayServiceImplFilterTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);
    private static final LocalDate DEPARTING_ON = LocalDate.of(2026, 8, 15);

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

    private UUID hotelId;

    @BeforeEach
    void setUp() {
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

    @Test
    void shouldFilterByDepartureDayAndAlloggiatiFailure() {
        final Stay stay = new Stay();
        final StayResponse dto = mock(StayResponse.class);
        when(stayRepository
                .findByHotelIdAndActualCheckInTimeBetweenAndStatusInAndExpectedCheckOutDateBetweenAndAlloggiatiSendFailedIn(
                        ArgumentMatchers.eq(hotelId), ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.eq(Set.of(StayStatus.CHECKED_IN)),
                        ArgumentMatchers.eq(DEPARTING_ON), ArgumentMatchers.eq(DEPARTING_ON),
                        ArgumentMatchers.eq(List.of(Boolean.TRUE)), ArgumentMatchers.eq(PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(stay), PAGEABLE, 1L));
        when(stayMapper.toDto(stay)).thenReturn(dto);

        final Page<StayResponse> response = stayService.getAllStays(
                PAGEABLE, hotelId, null, null, StayStatus.CHECKED_IN, DEPARTING_ON, true);

        assertEquals(dto, response.getContent().get(0));
    }

    @Test
    void shouldAcceptAnyFailureFlagWhenOnlyDepartureDayIsGiven() {
        when(stayRepository
                .findByHotelIdAndActualCheckInTimeBetweenAndStatusInAndExpectedCheckOutDateBetweenAndAlloggiatiSendFailedIn(
                        ArgumentMatchers.eq(hotelId), ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.eq(EnumSet.allOf(StayStatus.class)),
                        ArgumentMatchers.eq(DEPARTING_ON), ArgumentMatchers.eq(DEPARTING_ON),
                        ArgumentMatchers.eq(List.of(Boolean.TRUE, Boolean.FALSE)), ArgumentMatchers.eq(PAGEABLE)))
                .thenReturn(Page.empty(PAGEABLE));

        final Page<StayResponse> response = stayService.getAllStays(
                PAGEABLE, hotelId, null, null, null, DEPARTING_ON, false);

        assertEquals(0, response.getTotalElements());
    }

    @Test
    void shouldFallBackToPlainFilterWhenNoDepartureOrFailureFilter() {
        when(stayRepository.findByHotelIdAndActualCheckInTimeBetweenAndStatusIn(
                ArgumentMatchers.eq(hotelId), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.eq(EnumSet.allOf(StayStatus.class)), ArgumentMatchers.eq(PAGEABLE)))
                .thenReturn(Page.empty(PAGEABLE));

        stayService.getAllStays(PAGEABLE, hotelId, null, null, null, null, false);

        verify(stayRepository).findByHotelIdAndActualCheckInTimeBetweenAndStatusIn(
                ArgumentMatchers.eq(hotelId), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.eq(EnumSet.allOf(StayStatus.class)), ArgumentMatchers.eq(PAGEABLE));
    }
}
