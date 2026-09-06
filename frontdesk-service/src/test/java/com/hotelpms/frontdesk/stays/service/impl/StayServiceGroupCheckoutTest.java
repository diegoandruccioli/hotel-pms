package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GatewayEventsClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.GroupChargeRequest;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceForEmailResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationGroupBillingInfo;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StayServiceImpl}'s group master-folio charge transfer at
 * checkout (Punto 4, reservation groups). Split out of {@link StayServiceImplTest}
 * (Point 7 tenant-isolation audit pass) purely to keep that file under Checkstyle's
 * FileLength limit -- same fixture-construction pattern, own leaf mocks so this
 * class stays fully independent of the other one.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StayServiceGroupCheckoutTest {

    private static final String GUEST_FIRST_NAME = "John";
    private static final String GUEST_LAST_NAME = "Doe";
    private static final String GUEST_EMAIL = "john@example.com";
    private static final String ROOM_NUMBER_101 = "101";
    private static final String OPEN_STATUS = "ISSUED";
    private static final String HOTEL_NAME_TEST = "Hotel Test";
    private static final String INVOICE_NUMBER_TEST = "2026/0001";
    private static final String CURRENCY_EUR = "EUR";
    private static final int ROOM_CHARGE_NIGHTS = 2;
    private static final BigDecimal ROOM_CHARGE_UNIT_PRICE = BigDecimal.valueOf(100);
    private static final BigDecimal NIGHTLY_RATE = BigDecimal.valueOf(90);

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

    private UUID stayId;
    private UUID guestId;
    private UUID reservationId;
    private UUID roomId;
    private UUID hotelId;
    private Stay savedStay;
    private StayResponse validResponse;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        hotelId = UUID.randomUUID();

        savedStay = Stay.builder()
                .id(stayId)
                .reservationId(reservationId)
                .guestId(guestId)
                .roomId(roomId)
                .roomNumber(ROOM_NUMBER_101)
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .expectedCheckOutDate(LocalDate.now().plusDays(3))
                .build();

        validResponse = new StayResponse(stayId, null, reservationId, guestId, roomId,
                StayStatus.CHECKED_IN, savedStay.getActualCheckInTime(), null,
                LocalDateTime.now(), LocalDateTime.now(), null, false, false, null, new ArrayList<>(), null, null,
                null, false, null, false, null, null, null);

        lenient()
                .when(reservationService.getReservedRoomCharge(ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        lenient()
                .when(ratePricingService.resolveStayRates(ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final LocalDate checkIn = invocation.getArgument(2);
                    final LocalDate checkOut = invocation.getArgument(3);
                    final long nights = Math.max(1, ChronoUnit.DAYS.between(checkIn, checkOut));
                    final List<NightlyRate> rates = new ArrayList<>();
                    for (long i = 0; i < nights; i++) {
                        rates.add(new NightlyRate(checkIn.plusDays(i), NIGHTLY_RATE, null));
                    }
                    return rates;
                });

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

        lenient()
                .when(stayMapper.toDto(ArgumentMatchers.any(Stay.class), ArgumentMatchers.any()))
                .thenAnswer(invocation -> stayMapper.toDto(invocation.getArgument(0)));
    }

    private ReservationResponse reservationResponse(
            final ReservationStatus status, final List<ReservationLineItemResponse> lineItems) {
        return new ReservationResponse(reservationId, guestId, null, 2, 0,
                LocalDate.now(), LocalDate.now().plusDays(3), status, lineItems, true, null, null, false, null, null);
    }

    @Test
    void shouldTransferChargesToMasterFolioAndCheckOutWhenRoomIsBilledToGroup() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);
        checkedInStay.setRoomChargeId(UUID.randomUUID());
        checkedInStay.setRoomChargeUnitPrice(ROOM_CHARGE_UNIT_PRICE);
        checkedInStay.setRoomChargeNights(ROOM_CHARGE_NIGHTS);

        final UUID groupId = Objects.requireNonNull(UUID.randomUUID());
        final UUID invoiceId = UUID.randomUUID();
        // After the transfer, the individual invoice sits at ISSUED/zero (nothing left
        // to pay -- no F&B) instead of PAID, which the new checkout guard must still clear.
        final InvoiceStatusResponse zeroBalanceInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, OPEN_STATUS, BigDecimal.ZERO);
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(reservationService.getGroupBillingInfo(reservationId, hotelId))
                .thenReturn(Optional.of(new ReservationGroupBillingInfo(groupId, true)));
        when(billingClient.addChargeToGroupFolio(
                ArgumentMatchers.eq(groupId), ArgumentMatchers.any(GroupChargeRequest.class)))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(zeroBalanceInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);
        when(reservationService.getReservationById(reservationId))
                .thenReturn(reservationResponse(ReservationStatus.CHECKED_IN, List.of(lineItem)));
        when(stayRepository.findAllByReservationId(reservationId)).thenReturn(List.of(checkedInStay));
        when(guestClient.getGuestById(guestId))
                .thenReturn(new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(billingClient.getInvoiceForEmail(invoiceId))
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, OPEN_STATUS,
                        BigDecimal.ZERO, CURRENCY_EUR, List.of()));
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(true);

        // Act
        final StayResponse response = stayService.checkOut(id, hotelId);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_OUT, checkedInStay.getStatus());
        assertTrue(checkedInStay.isChargesTransferredToMasterFolio());
        verify(billingClient).removeCharge(id, checkedInStay.getRoomChargeId());
        verify(billingClient).addChargeToGroupFolio(
                ArgumentMatchers.eq(groupId), ArgumentMatchers.any(GroupChargeRequest.class));
    }

    @Test
    void shouldNotRetransferChargesOnARetriedCheckOutAfterAlreadyTransferred() {
        // Arrange: the stay already has chargesTransferredToMasterFolio=true from a
        // prior (successful) check-out attempt -- a retry must not re-transfer.
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);
        checkedInStay.setRoomChargeId(UUID.randomUUID());
        checkedInStay.setChargesTransferredToMasterFolio(true);

        final UUID groupId = Objects.requireNonNull(UUID.randomUUID());
        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse zeroBalanceInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, OPEN_STATUS, BigDecimal.ZERO);
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(reservationService.getGroupBillingInfo(reservationId, hotelId))
                .thenReturn(Optional.of(new ReservationGroupBillingInfo(groupId, true)));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(zeroBalanceInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);
        when(reservationService.getReservationById(reservationId))
                .thenReturn(reservationResponse(ReservationStatus.CHECKED_IN, List.of(lineItem)));
        when(stayRepository.findAllByReservationId(reservationId)).thenReturn(List.of(checkedInStay));
        when(guestClient.getGuestById(guestId))
                .thenReturn(new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(billingClient.getInvoiceForEmail(invoiceId))
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, OPEN_STATUS,
                        BigDecimal.ZERO, CURRENCY_EUR, List.of()));
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(true);

        // Act
        stayService.checkOut(id, hotelId);

        // Assert
        verify(billingClient, never()).removeCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(billingClient, never())
                .addChargeToGroupFolio(ArgumentMatchers.any(), ArgumentMatchers.any(GroupChargeRequest.class));
    }
}
