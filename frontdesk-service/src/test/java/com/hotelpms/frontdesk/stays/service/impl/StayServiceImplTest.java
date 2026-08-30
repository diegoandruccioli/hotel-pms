package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GatewayEventsClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.GatewayEventNotifyRequest;
import com.hotelpms.frontdesk.client.dto.GatewayEventNotifyRequest.GatewayEventType;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceForEmailResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.NotificationCheckoutRequest;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.BillingNotPaidException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservedRoomCharge;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.mapper.StayMapper;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.lang.NonNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StayServiceImpl}.
 *
 * <p>Room and reservation lookups/updates are now in-process calls to
 * {@link RoomService} / {@link ReservationService} (formerly Feign clients to
 * inventory-service / reservation-service — ADR-001). Guest and billing remain
 * Feign ({@link GuestClient} / {@link BillingClient}).
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StayServiceImplTest {

    private static final String GUEST_FIRST_NAME = "John";
    private static final String GUEST_LAST_NAME = "Doe";
    private static final String GUEST_EMAIL = "john@example.com";
    private static final String ROOM_NUMBER_101 = "101";
    private static final BigDecimal RESERVED_PRICE_310 = BigDecimal.valueOf(310);
    private static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    private static final String PS_PORTAL_DOWN = "PS portal down";
    private static final String PAID_STATUS = "PAID";
    private static final String OPEN_STATUS = "ISSUED";
    private static final String BILLING_SERVICE_UNAVAILABLE = "BILLING_SERVICE_UNAVAILABLE";
    private static final String HOTEL_NAME_TEST = "Hotel Test";
    private static final String INVOICE_NUMBER_TEST = "2026/0001";
    private static final String CURRENCY_EUR = "EUR";
    private static final BigDecimal INVOICE_TOTAL_200 = BigDecimal.valueOf(200);
    private static final BigDecimal INVOICE_TOTAL_80 = BigDecimal.valueOf(80);
    private static final int CITY_TAX_TOTAL_AMOUNT = 5;

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

    // Not mocked: StayServiceImpl now delegates to these collaborators instead of
    // doing the work inline (P10 SRP refactor). Building them for real out of the
    // same 9 leaf mocks above — rather than mocking the collaborators themselves —
    // means every existing test below keeps verifying the real behavior (e.g.
    // billingClient.createInvoiceForStay(...)) two hops down, unchanged.
    private StayServiceImpl stayService;

    private UUID stayId = UUID.randomUUID();
    private UUID guestId = UUID.randomUUID();
    private UUID reservationId = UUID.randomUUID();
    private UUID roomId = UUID.randomUUID();
    private UUID hotelId = UUID.randomUUID();

    private StayRequest validRequest = new StayRequest(hotelId, reservationId, guestId, roomId,
            StayStatus.EXPECTED, null, null, null, new ArrayList<>());
    private Stay savedStay = new Stay();
    private StayResponse validResponse;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        hotelId = UUID.randomUUID();

        validRequest = new StayRequest(hotelId, reservationId, guestId, roomId,
                StayStatus.EXPECTED, null, null, null, new ArrayList<>());

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
                null, false, null, false, null, null);

        // StayBillingCoordinator now prefers a reservation's snapshotted price over a
        // live resolve (the P10-follow-up reconciliation fix); defaulting the snapshot
        // lookup to "not found" here makes every existing test below fall through to
        // live resolution via ratePricingService — same code path StayBillingCoordinator
        // always used before this fix, so the basePrice=90-based assertions below are
        // unchanged. resolveStayRates mirrors the old "basePrice * nights" math exactly
        // (uniform 90/night for however many nights are requested) rather than a fixed
        // list, since different tests use different check-in/check-out spans.
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
                        rates.add(new NightlyRate(checkIn.plusDays(i), BigDecimal.valueOf(90), null));
                    }
                    return rates;
                });

        stayService = new StayServiceImpl(
                stayRepository, stayGuestRepository, stayMapper, guestClient, roomService, reservationService,
                new StayCheckInValidator(guestClient, reservationService, roomService),
                new StayBillingCoordinator(billingClient, roomService, stayRepository, reservationService,
                        ratePricingService, cityTaxAssessmentService),
                new StayAlloggiatiCoordinator(alloggiatiWebSenderService, hotelSettingsService, stayRepository),
                new StayNotificationCoordinator(
                        notificationClient, guestClient, billingClient, hotelSettingsService, stayRepository),
                new StayReservationSync(reservationService, stayRepository),
                gatewayEventsClient,
                cityTaxAssessmentService);
    }

    private ReservationResponse reservationResponse(
            final ReservationStatus status, final List<ReservationLineItemResponse> lineItems) {
        return new ReservationResponse(reservationId, guestId, null, 2, 0,
                LocalDate.now(), LocalDate.now().plusDays(3), status, lineItems, true, null, null, false, null, null);
    }

    private RoomResponse room() {
        final RoomTypeResponse roomType = new RoomTypeResponse(
                UUID.randomUUID(), "Standard", null, 2, BigDecimal.valueOf(90), true, null, null);
        return new RoomResponse(roomId, hotelId, ROOM_NUMBER_101, roomType, RoomStatus.CLEAN, true, null, null, null);
    }

    @Test
    void shouldCheckInSuccessfully() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        final StayResponse expected = Objects.requireNonNull(validResponse);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        // Act
        final StayResponse response = stayService.checkIn(request);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_IN, response.status());
        verify(guestClient, times(1)).getGuestById(guest);
        verify(reservationService, times(2)).getReservationById(reservation);
        verify(roomService, times(1)).getRoomById(room, hotelId);
        verify(roomService, times(1)).updateRoomStatus(room, null, RoomStatus.OCCUPIED);
        verify(stayRepository, times(1)).save(Objects.requireNonNull(unmappedStay));
        assertEquals(StayStatus.CHECKED_IN, unmappedStay.getStatus());
        assertNotNull(unmappedStay.getActualCheckInTime());
        verify(gatewayEventsClient, times(1))
                .notify(new GatewayEventNotifyRequest(GatewayEventType.CHECK_IN));
    }

    @Test
    void shouldAbortCheckInWhenRoomNotFound() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenThrow(new NotFoundException(ROOM_NOT_FOUND));

        // Act & Assert — room lookup is now in-process: a missing room propagates
        // NotFoundException directly, there is no network failure mode to wrap.
        final NotFoundException exception = assertThrows(NotFoundException.class,
                () -> stayService.checkIn(request));
        assertEquals(ROOM_NOT_FOUND, exception.getMessage());

        verify(stayRepository, times(0)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldCheckInAndSetStayForGuests() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        final StayResponse expected = Objects.requireNonNull(validResponse);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        final List<StayGuest> guests = new ArrayList<>();
        guests.add(new StayGuest());
        unmappedStay.setGuests(guests);

        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        // Act
        stayService.checkIn(request);

        // Assert
        assertEquals(unmappedStay, guests.get(0).getStay());
    }

    @Test
    void shouldUpdateReservationStatusToPartiallyCheckedIn() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        final StayResponse expected = Objects.requireNonNull(validResponse);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));

        final ReservationLineItemResponse lineItem1 =
                new ReservationLineItemResponse(UUID.randomUUID(), room, BigDecimal.TEN, true, null, null);
        final ReservationLineItemResponse lineItem2 =
                new ReservationLineItemResponse(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, true, null, null);
        final List<ReservationLineItemResponse> lineItems = List.of(lineItem1, lineItem2);

        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, lineItems));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        // Mock stayRepository.findAllByReservationId to return 1 stay (less than 2 rooms)
        final Stay existingStay = new Stay();
        final List<StayGuest> existingGuests = new ArrayList<>();
        existingGuests.add(new StayGuest());
        existingStay.setGuests(existingGuests);
        when(stayRepository.findAllByReservationId(reservation)).thenReturn(List.of(existingStay));

        // Act
        stayService.checkIn(request);

        // Assert
        verify(reservationService, times(1)).updateStatusAndGuests(
                ArgumentMatchers.eq(reservation),
                ArgumentMatchers.eq(ReservationStatus.PARTIALLY_CHECKED_IN),
                ArgumentMatchers.eq(1));
    }

    @Test
    void shouldUpdateReservationStatusToCheckedIn() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        final StayResponse expected = Objects.requireNonNull(validResponse);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));

        final ReservationLineItemResponse lineItem1 =
                new ReservationLineItemResponse(UUID.randomUUID(), room, BigDecimal.TEN, true, null, null);
        final List<ReservationLineItemResponse> lineItems = List.of(lineItem1);

        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, lineItems));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        final Stay existingStay = new Stay();
        final List<StayGuest> existingGuests = new ArrayList<>();
        existingGuests.add(new StayGuest());
        existingGuests.add(new StayGuest());
        existingStay.setGuests(existingGuests);
        when(stayRepository.findAllByReservationId(reservation)).thenReturn(List.of(existingStay));

        // Act
        stayService.checkIn(request);

        // Assert
        verify(reservationService, times(1)).updateStatusAndGuests(
                ArgumentMatchers.eq(reservation),
                ArgumentMatchers.eq(ReservationStatus.CHECKED_IN),
                ArgumentMatchers.eq(2));
    }

    @Test
    void shouldGetStayByIdSuccessfully() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final StayResponse expectedResponse = Objects.requireNonNull(validResponse);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(stayMapper.toDto(stay)).thenReturn(expectedResponse);

        // Act
        final StayResponse response = stayService.getStayById(id, hotelId);

        // Assert
        assertNotNull(response);
        assertEquals(id, response.id());
    }

    @Test
    void shouldThrowNotFoundWhenStayDoesNotExist() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> stayService.getStayById(id, hotelId));
    }

    @Test
    void shouldGetAllStaysScopedToHotelId() {
        // Arrange
        final Stay stay = Objects.requireNonNull(savedStay);
        final StayResponse expectedResponse = Objects.requireNonNull(validResponse);
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Stay> stayPage = new PageImpl<>(List.of(stay), pageable, 1L);

        when(stayRepository.findByHotelId(hotelId, pageable)).thenReturn(stayPage);
        when(stayMapper.toDto(stay)).thenReturn(expectedResponse);

        // Act
        final Page<StayResponse> response = stayService.getAllStays(pageable, hotelId);

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(expectedResponse, response.getContent().get(0));
        verify(stayRepository, times(1)).findByHotelId(hotelId, pageable);
    }

    @Test
    void shouldGetStaysByReservationIdScopedToHotelId() {
        // Arrange
        final UUID reservation = Objects.requireNonNull(reservationId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final StayResponse expectedResponse = Objects.requireNonNull(validResponse);
        final Pageable pageable = PageRequest.of(0, 20);

        when(stayRepository.findAllByReservationIdAndHotelId(reservation, hotelId))
                .thenReturn(List.of(stay));
        when(stayMapper.toDto(stay)).thenReturn(expectedResponse);

        // Act
        final Page<StayResponse> response = stayService.getStaysByReservationId(reservation, hotelId, pageable);

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(expectedResponse, response.getContent().get(0));
        verify(stayRepository, times(1)).findAllByReservationIdAndHotelId(reservation, hotelId);
    }

    @Test
    void shouldGetStaysByReservationIdReturnCorrectSliceForSecondPage() {
        // Arrange: 3 stays, page size 2 — second page must contain only the
        // remaining 1 stay, not the full unpaginated list (regression guard for
        // the fake-PageImpl bug where pageable was ignored entirely).
        final UUID reservation = Objects.requireNonNull(reservationId);
        final Stay stayA = Objects.requireNonNull(savedStay);
        final Stay stayB = new Stay();
        final Stay stayC = new Stay();
        final StayResponse responseA = Objects.requireNonNull(validResponse);
        final StayResponse responseB = mock(StayResponse.class);
        final StayResponse responseC = mock(StayResponse.class);
        final Pageable secondPage = PageRequest.of(1, 2);

        when(stayRepository.findAllByReservationIdAndHotelId(reservation, hotelId))
                .thenReturn(List.of(stayA, stayB, stayC));
        when(stayMapper.toDto(stayA)).thenReturn(responseA);
        when(stayMapper.toDto(stayB)).thenReturn(responseB);
        when(stayMapper.toDto(stayC)).thenReturn(responseC);

        // Act
        final Page<StayResponse> response = stayService.getStaysByReservationId(reservation, hotelId, secondPage);

        // Assert
        assertEquals(3, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals(responseC, response.getContent().get(0));
    }

    @Test
    void shouldGetAllStaysWithDateRangeAndStatusFilter() {
        // Arrange
        final Stay stay = Objects.requireNonNull(savedStay);
        final StayResponse expectedResponse = Objects.requireNonNull(validResponse);
        final Pageable pageable = PageRequest.of(0, 20);
        final LocalDate dateFrom = LocalDate.of(2026, 8, 1);
        final LocalDate dateTo = LocalDate.of(2026, 8, 31);
        final Page<Stay> stayPage = new PageImpl<>(List.of(stay), pageable, 1L);

        when(stayRepository.findByHotelIdAndActualCheckInTimeBetweenAndStatusIn(
                hotelId, dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(),
                java.util.Set.of(StayStatus.CHECKED_IN), pageable))
                .thenReturn(stayPage);
        when(stayMapper.toDto(stay)).thenReturn(expectedResponse);

        // Act
        final Page<StayResponse> response = stayService.getAllStays(
                pageable, hotelId, dateFrom, dateTo, StayStatus.CHECKED_IN);

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(expectedResponse, response.getContent().get(0));
    }

    @Test
    void shouldGetAllStaysWithNoFiltersDefaultToWidestRangeAndAllStatuses() {
        // Arrange
        final Pageable pageable = PageRequest.of(0, 20);

        when(stayRepository.findByHotelIdAndActualCheckInTimeBetweenAndStatusIn(
                ArgumentMatchers.eq(hotelId), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.eq(java.util.EnumSet.allOf(StayStatus.class)), ArgumentMatchers.eq(pageable)))
                .thenReturn(Page.empty(pageable));

        // Act
        stayService.getAllStays(pageable, hotelId, null, null, null);

        // Assert
        verify(stayRepository).findByHotelIdAndActualCheckInTimeBetweenAndStatusIn(
                ArgumentMatchers.eq(hotelId), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.eq(java.util.EnumSet.allOf(StayStatus.class)), ArgumentMatchers.eq(pageable));
    }

    @Test
    void shouldCheckOutSuccessfully() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, PAID_STATUS, BigDecimal.valueOf(200));
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
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
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, PAID_STATUS,
                        INVOICE_TOTAL_200, CURRENCY_EUR, List.of()));
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(true);

        // Act
        final StayResponse response = stayService.checkOut(id, hotelId);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_OUT, checkedInStay.getStatus());
        assertNotNull(checkedInStay.getActualCheckOutTime());
        assertFalse(checkedInStay.isCheckoutEmailFailed());
        verify(roomService, times(1)).updateRoomStatus(Objects.requireNonNull(roomId), hotelId, RoomStatus.DIRTY);
        verify(reservationService, times(1))
                .updateStatusAndGuests(reservationId, ReservationStatus.CHECKED_OUT, null);
        verify(notificationClient, times(1)).sendCheckout(ArgumentMatchers.any());
        verify(gatewayEventsClient, times(1))
                .notify(new GatewayEventNotifyRequest(GatewayEventType.CHECK_OUT));
    }

    @Test
    void shouldAttachInvoicePdfToCheckoutEmailWhenBillingServiceProvidesIt() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, PAID_STATUS, BigDecimal.valueOf(200));
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);
        final byte[] pdfBytes = {1, 2, 3};

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
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
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, PAID_STATUS,
                        INVOICE_TOTAL_200, CURRENCY_EUR, List.of()));
        when(billingClient.getInvoicePdf(invoiceId)).thenReturn(pdfBytes);
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(true);
        final ArgumentCaptor<NotificationCheckoutRequest> captor =
                ArgumentCaptor.forClass(NotificationCheckoutRequest.class);

        // Act
        stayService.checkOut(id, hotelId);

        // Assert
        verify(notificationClient).sendCheckout(captor.capture());
        assertNotNull(captor.getValue().invoicePdf());
        assertEquals("fattura-" + invoiceId + ".pdf", captor.getValue().invoiceFileName());
    }

    @Test
    void shouldSendCheckoutEmailWithoutAttachmentWhenBillingServiceCannotProducePdf() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, PAID_STATUS, BigDecimal.valueOf(200));
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
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
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, PAID_STATUS,
                        INVOICE_TOTAL_200, CURRENCY_EUR, List.of()));
        when(billingClient.getInvoicePdf(invoiceId)).thenReturn(null);
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(true);
        final ArgumentCaptor<NotificationCheckoutRequest> captor =
                ArgumentCaptor.forClass(NotificationCheckoutRequest.class);

        // Act
        final StayResponse response = stayService.checkOut(id, hotelId);

        // Assert — the missing attachment never blocks the checkout email itself
        assertNotNull(response);
        verify(notificationClient).sendCheckout(captor.capture());
        assertNull(captor.getValue().invoicePdf());
        assertNull(captor.getValue().invoiceFileName());
    }

    @Test
    void shouldMarkCheckoutEmailFailedWhenNotificationServiceUnavailable() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, PAID_STATUS, BigDecimal.valueOf(200));
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
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
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, PAID_STATUS,
                        INVOICE_TOTAL_200, CURRENCY_EUR, List.of()));
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(false);

        // Act
        stayService.checkOut(id, hotelId);

        // Assert
        assertTrue(checkedInStay.isCheckoutEmailFailed());
        assertEquals("NOTIFICATION_SERVICE_UNAVAILABLE", checkedInStay.getCheckoutEmailFailureReason());
    }

    @Test
    void shouldSkipCheckoutEmailWhenDisabledByHotelSettings() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, PAID_STATUS, BigDecimal.valueOf(200));
        final ReservationLineItemResponse lineItem =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);
        when(reservationService.getReservationById(reservationId))
                .thenReturn(reservationResponse(ReservationStatus.CHECKED_IN, List.of(lineItem)));
        when(stayRepository.findAllByReservationId(reservationId)).thenReturn(List.of(checkedInStay));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, false, null, null, null, null, null, null, null));

        // Act
        final StayResponse response = stayService.checkOut(id, hotelId);

        // Assert
        assertNotNull(response);
        verify(notificationClient, never()).sendCheckout(ArgumentMatchers.any());
        verify(guestClient, never()).getGuestById(ArgumentMatchers.any());
    }

    @Test
    void shouldNotCheckOutReservationWhenOtherRoomsStillCheckedIn() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);
        checkedInStay.setHotelId(hotelId);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, reservationId, PAID_STATUS, BigDecimal.valueOf(200));
        final ReservationLineItemResponse lineItem1 =
                new ReservationLineItemResponse(UUID.randomUUID(), roomId, BigDecimal.TEN, true, null, null);
        final ReservationLineItemResponse lineItem2 =
                new ReservationLineItemResponse(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, true, null, null);
        final Stay stillCheckedInStay = Stay.builder()
                .id(UUID.randomUUID())
                .reservationId(reservationId)
                .roomId(lineItem2.roomId())
                .status(StayStatus.CHECKED_IN)
                .build();

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);
        when(reservationService.getReservationById(reservationId))
                .thenReturn(reservationResponse(ReservationStatus.CHECKED_IN, List.of(lineItem1, lineItem2)));
        when(stayRepository.findAllByReservationId(reservationId))
                .thenReturn(List.of(checkedInStay, stillCheckedInStay));
        when(guestClient.getGuestById(guestId))
                .thenReturn(new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(billingClient.getInvoiceForEmail(invoiceId))
                .thenReturn(new InvoiceForEmailResponse(invoiceId, reservationId, INVOICE_NUMBER_TEST, PAID_STATUS,
                        INVOICE_TOTAL_200, CURRENCY_EUR, List.of()));

        // Act
        stayService.checkOut(id, hotelId);

        // Assert
        verify(reservationService, times(0))
                .updateStatusAndGuests(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void shouldThrowWhenCheckOutBillingNotPaid() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setReservationId(reservationId);

        final InvoiceStatusResponse unpaidInvoice = new InvoiceStatusResponse(
                UUID.randomUUID(), reservationId, "ISSUED", BigDecimal.valueOf(200));

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(unpaidInvoice);

        // Act & Assert
        assertThrows(BillingNotPaidException.class, () -> stayService.checkOut(id, hotelId));
    }

    @Test
    void shouldCheckOutWalkInStaySuccessfullyByInvoiceId() {
        // Arrange — walk-in: no reservationId, invoice looked up by invoiceId instead
        final UUID id = Objects.requireNonNull(stayId);
        final UUID invoiceId = UUID.randomUUID();
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(null);
        checkedInStay.setInvoiceId(invoiceId);
        checkedInStay.setHotelId(hotelId);

        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, null, PAID_STATUS, BigDecimal.valueOf(80));

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getInvoiceById(invoiceId)).thenReturn(paidInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);
        when(guestClient.getGuestById(guestId))
                .thenReturn(new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(billingClient.getInvoiceForEmail(invoiceId))
                .thenReturn(new InvoiceForEmailResponse(invoiceId, null, INVOICE_NUMBER_TEST, PAID_STATUS,
                        INVOICE_TOTAL_80, CURRENCY_EUR, List.of()));

        // Act
        final StayResponse response = stayService.checkOut(id, hotelId);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_OUT, checkedInStay.getStatus());
        verify(billingClient, times(0)).getLatestInvoiceByReservation(ArgumentMatchers.any());
    }

    @Test
    void shouldThrowWhenCheckOutWalkInStayHasNoInvoiceId() {
        // Arrange — walk-in whose invoice was never created (billing-service was
        // down at check-in): no reservationId AND no invoiceId, nothing to verify
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setReservationId(null);
        checkedInStay.setInvoiceId(null);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));

        // Act & Assert
        assertThrows(BillingNotPaidException.class, () -> stayService.checkOut(id, hotelId));
        verifyNoInteractions(billingClient);
    }

    @Test
    void shouldThrowWhenCheckOutStayNotCheckedIn() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay notCheckedInStay = Stay.builder()
                .id(id)
                .status(StayStatus.EXPECTED)
                .build();

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(notCheckedInStay));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkOut(id, hotelId));
    }

    @Test
    void shouldRejectCheckInWhenReservationIsCancelled() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CANCELLED, null));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkIn(request));
        verify(stayRepository, times(0)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldRejectCheckInWhenReservationIsCheckedOut() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CHECKED_OUT, null));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkIn(request));
        verify(stayRepository, times(0)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldRejectCheckInWhenReservationIsNoShow() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.NO_SHOW, null));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkIn(request));
        verify(stayRepository, times(0)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldAllowCheckInWhenReservationIsPartiallyCheckedIn() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        final StayResponse expected = Objects.requireNonNull(validResponse);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.PARTIALLY_CHECKED_IN, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        // Act
        final StayResponse response = stayService.checkIn(request);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_IN, response.status());
        verify(stayRepository, times(1)).save(Objects.requireNonNull(unmappedStay));
    }

    @Test
    void shouldOpenInvoiceInBillingServiceOnCheckIn() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        saved.setHotelId(hotelId);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        final UUID invoiceId = UUID.randomUUID();
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(invoiceId));
        when(billingClient.addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert
        verify(billingClient, times(1)).createInvoiceForStay(anyNonNull(StayInvoiceRequest.class));
        verify(billingClient, times(1)).addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any());
        assertEquals(invoiceId, saved.getInvoiceId());
        assertFalse(saved.isInvoiceCreationFailed());
        verify(stayRepository, times(2)).save(anyNonNull(Stay.class));
    }

    /**
     * Regression test for a bug found during live smoke-testing of the P10
     * follow-up reconciliation fix: a guest checking in later or earlier than
     * the reservation's booked check-in date (a completely ordinary late/early
     * arrival) must still see the room-charge {@code nights} match the
     * reservation's own dates — the same dates {@code amount} was computed
     * from — not the actual check-in moment. Before this fix, {@code nights}
     * was derived from {@code stay.actualCheckInTime}, which can legitimately
     * differ from the reservation's {@code checkInDate}, producing an invoice
     * description ("N night(s)") inconsistent with the amount actually billed.
     */
    @Test
    void shouldUseReservationNightsNotActualCheckInTimeWhenBillingAReservationBasedStay() {
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        saved.setHotelId(hotelId);
        // Actual check-in is "now"; expectedCheckOutDate (set in setUp) is 3 days
        // out from "now" — if nights were (still, buggily) derived from these two,
        // it would compute 3, not the reservation's real 2-night span asserted below.
        saved.setActualCheckInTime(LocalDateTime.now());

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(reservationService.getReservedRoomCharge(reservation, room, hotelId))
                .thenReturn(Optional.of(new ReservedRoomCharge(RESERVED_PRICE_310, 2)));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        final UUID invoiceId = UUID.randomUUID();
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(invoiceId));
        final ArgumentCaptor<ChargeRequest> chargeCaptor = ArgumentCaptor.forClass(ChargeRequest.class);
        when(billingClient.addCharge(ArgumentMatchers.eq(stayId), chargeCaptor.capture()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        stayService.checkIn(request);

        final ChargeRequest charge = chargeCaptor.getValue();
        assertEquals(RESERVED_PRICE_310, charge.amount());
        assertEquals(2, charge.nights());
        assertTrue(charge.description().contains("2 night(s)"));
    }

    @Test
    void shouldMarkInvoiceCreationFailedWhenBillingServiceUnavailable() {
        // Arrange — circuit-breaker fallback returns null (BillingClient.createInvoiceForStayFallback)
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class))).thenReturn(null);
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert — check-in still succeeds (non-blocking), but the failure is now durable
        assertNull(saved.getInvoiceId());
        assertTrue(saved.isInvoiceCreationFailed());
        assertEquals(BILLING_SERVICE_UNAVAILABLE, saved.getInvoiceCreationFailureReason());
    }

    @Test
    void shouldRecordRealReasonWhenBillingRejectsInvoiceCreation() {
        // Arrange — round 1 bug #1: a legitimate 4xx from billing-service (e.g. a
        // stale-retry race on INVOICE_ALREADY_EXISTS_FOR_STAY) must be recorded with
        // its real reason, not the generic BILLING_SERVICE_UNAVAILABLE — check-in
        // still completes (backup/DECISIONS.md §2.2), only the failure reason changes.
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        final feign.FeignException.Conflict conflict = mock(feign.FeignException.Conflict.class);
        when(conflict.getMessage()).thenReturn("INVOICE_ALREADY_EXISTS_FOR_STAY");
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class))).thenThrow(conflict);
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert
        assertTrue(saved.isInvoiceCreationFailed());
        assertEquals("INVOICE_ALREADY_EXISTS_FOR_STAY", saved.getInvoiceCreationFailureReason());
    }

    @Test
    void shouldRetryInvoiceCreationAndClearFailedFlag() {
        // Arrange
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        stay.setInvoiceCreationFailed(true);
        stay.setInvoiceCreationFailureReason(BILLING_SERVICE_UNAVAILABLE);

        final UUID invoiceId = UUID.randomUUID();
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(invoiceId));
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(room());
        when(billingClient.addCharge(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        final StayResponse response = stayService.retryInvoiceCreation(stayId, hotelId);

        // Assert
        assertNotNull(response);
        assertEquals(invoiceId, stay.getInvoiceId());
        assertFalse(stay.isInvoiceCreationFailed());
        assertNull(stay.getInvoiceCreationFailureReason());
    }

    @Test
    void shouldRetryOnlyChargeWhenInvoiceAlreadyCreatedButChargeFailed() {
        // Arrange — partial failure: invoice shell exists, room charge never attached
        final Stay stay = Objects.requireNonNull(savedStay);
        final UUID invoiceId = UUID.randomUUID();
        stay.setHotelId(hotelId);
        stay.setInvoiceId(invoiceId);
        stay.setInvoiceCreationFailed(true);
        stay.setInvoiceCreationFailureReason(BILLING_SERVICE_UNAVAILABLE);

        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(room());
        when(billingClient.addCharge(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.retryInvoiceCreation(stayId, hotelId);

        // Assert — invoice is NOT re-created, only the missing charge is retried
        verify(billingClient, never()).createInvoiceForStay(ArgumentMatchers.any());
        verify(billingClient, times(1)).addCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
        assertEquals(invoiceId, stay.getInvoiceId());
        assertFalse(stay.isInvoiceCreationFailed());
    }

    @Test
    void shouldSkipInvoiceFlowWhenAlreadyCompletedToAvoidDoubleBilling() {
        // Arrange — invoice + room charge already recorded successfully on a prior call
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        stay.setInvoiceId(UUID.randomUUID());
        stay.setInvoiceCreationFailed(false);

        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayMapper.toDto(stay)).thenReturn(Objects.requireNonNull(validResponse));

        // Act — a stray retry (e.g. double-click) must not re-bill the room
        stayService.retryInvoiceCreation(stayId, hotelId);

        // Assert
        verify(billingClient, never()).createInvoiceForStay(ArgumentMatchers.any());
        verify(billingClient, never()).addCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(stayRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void shouldPostCityTaxChargeAlongsideRoomNightWhenAssessed() {
        // Arrange — hotel has comune+category+rate configured, assessment yields a positive total
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        saved.setHotelId(hotelId);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        final UUID invoiceId = UUID.randomUUID();
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(invoiceId));
        final UUID roomChargeId = UUID.randomUUID();
        final UUID cityTaxChargeId = UUID.randomUUID();
        when(billingClient.addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(roomChargeId), new ChargeResponse(cityTaxChargeId));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        final UUID assessmentId = UUID.randomUUID();
        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .id(assessmentId)
                .hotelId(hotelId)
                .stayId(stayId)
                .totalAmount(BigDecimal.valueOf(CITY_TAX_TOTAL_AMOUNT))
                .taxableNights(2)
                .taxableGuests(1)
                .build();
        when(cityTaxAssessmentService.assessFor(ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(assessment));

        // Act
        stayService.checkIn(request);

        // Assert — two distinct charges posted, city-tax marked charged with its own id
        verify(billingClient, times(2)).addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any());
        verify(cityTaxAssessmentService, times(1)).markCharged(assessmentId, cityTaxChargeId);
        assertFalse(saved.isInvoiceCreationFailed());
    }

    @Test
    void shouldNotPostCityTaxChargeWhenAssessedTotalIsZero() {
        // Arrange — assessment exists (e.g. all guests age-exempt) but totals to zero:
        // no zero-amount line should ever be posted to billing-service
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        saved.setHotelId(hotelId);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        final UUID invoiceId = UUID.randomUUID();
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(invoiceId));
        when(billingClient.addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        final CityTaxAssessment zeroAssessment = CityTaxAssessment.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .stayId(stayId)
                .totalAmount(BigDecimal.ZERO)
                .taxableNights(2)
                .taxableGuests(0)
                .build();
        when(cityTaxAssessmentService.assessFor(ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(zeroAssessment));

        // Act
        stayService.checkIn(request);

        // Assert — only ROOM_NIGHT posted, city-tax assessment never marked charged
        verify(billingClient, times(1)).addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any());
        verify(cityTaxAssessmentService, never()).markCharged(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /**
     * Regression test for the per-charge idempotency guard added alongside CITY_TAX
     * (E18): before {@code Stay.roomChargeId} existed, the retry guard was a single
     * boolean shared by both charges, so a CITY_TAX-only failure on a prior attempt
     * would have caused the retry to re-post ROOM_NIGHT too — double-billing the room.
     */
    @Test
    void shouldRetryOnlyCityTaxChargeWithoutRePostingRoomNightWhenOnlyCityTaxFailedPreviously() {
        // Arrange — room charge already posted successfully on a prior attempt;
        // only the city-tax charge failed and must be the only thing retried
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        final UUID invoiceId = UUID.randomUUID();
        stay.setInvoiceId(invoiceId);
        final UUID roomChargeId = UUID.randomUUID();
        stay.setRoomChargeId(roomChargeId);
        stay.setInvoiceCreationFailed(true);
        stay.setInvoiceCreationFailureReason("CITY_TAX_POST_FAILED");

        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(room());
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(Objects.requireNonNull(validResponse));

        final UUID assessmentId = UUID.randomUUID();
        final UUID cityTaxChargeId = UUID.randomUUID();
        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .id(assessmentId)
                .hotelId(hotelId)
                .stayId(stayId)
                .totalAmount(BigDecimal.TEN)
                .taxableNights(2)
                .taxableGuests(2)
                .build();
        when(cityTaxAssessmentService.assessFor(ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(assessment));
        when(billingClient.addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(cityTaxChargeId));

        // Act
        stayService.retryInvoiceCreation(stayId, hotelId);

        // Assert — invoice not re-created, room charge not re-posted (still the
        // original id), only the missing city-tax charge is posted
        verify(billingClient, never()).createInvoiceForStay(ArgumentMatchers.any());
        verify(billingClient, times(1)).addCharge(ArgumentMatchers.eq(stayId), ArgumentMatchers.any());
        verify(cityTaxAssessmentService, times(1)).markCharged(assessmentId, cityTaxChargeId);
        assertEquals(roomChargeId, stay.getRoomChargeId());
        assertFalse(stay.isInvoiceCreationFailed());
    }

    @Test
    void shouldThrowNotFoundWhenRetryingInvoiceForUnknownStay() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> stayService.retryInvoiceCreation(stayId, hotelId));
    }

    @Test
    void shouldRetryCheckoutEmailAndClearFailedFlag() {
        // Arrange
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        stay.setStatus(StayStatus.CHECKED_OUT);
        stay.setInvoiceId(UUID.randomUUID());
        stay.setCheckoutEmailFailed(true);
        stay.setCheckoutEmailFailureReason("NOTIFICATION_SERVICE_UNAVAILABLE");

        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                stay.getInvoiceId(), reservationId, PAID_STATUS, BigDecimal.valueOf(200));

        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(billingClient.getLatestInvoiceByReservation(reservationId)).thenReturn(paidInvoice);
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(guestClient.getGuestById(guestId))
                .thenReturn(new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(billingClient.getInvoiceForEmail(stay.getInvoiceId()))
                .thenReturn(new InvoiceForEmailResponse(stay.getInvoiceId(), reservationId, INVOICE_NUMBER_TEST,
                        PAID_STATUS, INVOICE_TOTAL_200, CURRENCY_EUR, List.of()));
        when(notificationClient.sendCheckout(ArgumentMatchers.any())).thenReturn(true);
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        final StayResponse response = stayService.retryCheckoutEmail(stayId, hotelId);

        // Assert
        assertNotNull(response);
        assertFalse(stay.isCheckoutEmailFailed());
        assertNull(stay.getCheckoutEmailFailureReason());
    }

    @Test
    void shouldRejectCheckoutEmailRetryWhenStayNotCheckedOut() {
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        stay.setStatus(StayStatus.CHECKED_IN);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(IllegalStateException.class, () -> stayService.retryCheckoutEmail(stayId, hotelId));
    }

    @Test
    void shouldSendAlloggiatiAutomaticallyWhenAutoSendEnabled() {
        final UUID stayHotelId = UUID.randomUUID();
        final Stay stayWithHotel = Stay.builder()
                .id(stayId)
                .hotelId(stayHotelId)
                .reservationId(reservationId)
                .guestId(guestId)
                .roomId(roomId)
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .build();

        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(stayMapper.toEntity(request)).thenReturn(new Stay());
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(stayWithHotel);
        when(hotelSettingsService.getOrCreate(stayHotelId))
                .thenReturn(new HotelSettingsResponse(stayHotelId, true, null, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(stayMapper.toDto(stayWithHotel)).thenReturn(Objects.requireNonNull(validResponse));

        stayService.checkIn(request);

        final LocalDate expectedDate = stayWithHotel.getActualCheckInTime().toLocalDate();
        verify(alloggiatiWebSenderService, times(1)).submitReport(expectedDate, stayHotelId);
        assertTrue(stayWithHotel.isAlloggiatiSent());
    }

    @Test
    void shouldSkipAlloggiatiWhenAutoSendDisabled() {
        final UUID stayHotelId = UUID.randomUUID();
        final Stay stayWithHotel = Stay.builder()
                .id(stayId)
                .hotelId(stayHotelId)
                .reservationId(reservationId)
                .guestId(guestId)
                .roomId(roomId)
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .build();

        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(stayMapper.toEntity(request)).thenReturn(new Stay());
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(stayWithHotel);
        when(hotelSettingsService.getOrCreate(stayHotelId))
                .thenReturn(new HotelSettingsResponse(stayHotelId, false, null, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(stayMapper.toDto(stayWithHotel)).thenReturn(Objects.requireNonNull(validResponse));

        stayService.checkIn(request);

        verifyNoInteractions(alloggiatiWebSenderService);
        assertFalse(stayWithHotel.isAlloggiatiSent());
    }

    @Test
    void shouldNotBlockCheckInWhenAlloggiatiSendFails() {
        final UUID stayHotelId = UUID.randomUUID();
        final Stay stayWithHotel = Stay.builder()
                .id(stayId)
                .hotelId(stayHotelId)
                .reservationId(reservationId)
                .guestId(guestId)
                .roomId(roomId)
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .build();

        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(stayMapper.toEntity(request)).thenReturn(new Stay());
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(stayWithHotel);
        when(hotelSettingsService.getOrCreate(stayHotelId))
                .thenReturn(new HotelSettingsResponse(stayHotelId, true, null, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        doThrow(new ExternalServiceException(PS_PORTAL_DOWN, null))
                .when(alloggiatiWebSenderService)
                .submitReport(ArgumentMatchers.any(LocalDate.class), ArgumentMatchers.any(UUID.class));
        when(stayMapper.toDto(stayWithHotel)).thenReturn(Objects.requireNonNull(validResponse));

        final StayResponse response = stayService.checkIn(request);

        assertNotNull(response);
        assertFalse(stayWithHotel.isAlloggiatiSent());
        assertTrue(stayWithHotel.isAlloggiatiSendFailed());
        assertEquals(PS_PORTAL_DOWN, stayWithHotel.getAlloggiatiFailureReason());
    }

    @Test
    void shouldMarkStaysAsSentForDateAfterManualSubmit() {
        final UUID date1HotelId = Objects.requireNonNull(hotelId);
        final LocalDate date = LocalDate.now();
        final Stay previouslyFailed = Stay.builder()
                .id(UUID.randomUUID())
                .hotelId(date1HotelId)
                .alloggiatiSent(false)
                .alloggiatiSendFailed(true)
                .alloggiatiFailureReason(PS_PORTAL_DOWN)
                .build();

        when(stayRepository.findByActualCheckInTimeBetweenAndHotelId(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(date1HotelId)))
                .thenReturn(List.of(previouslyFailed));

        stayService.markAlloggiatiSentForDate(date, date1HotelId);

        assertTrue(previouslyFailed.isAlloggiatiSent());
        assertFalse(previouslyFailed.isAlloggiatiSendFailed());
        assertNull(previouslyFailed.getAlloggiatiFailureReason());
        verify(stayRepository, times(1)).saveAll(List.of(previouslyFailed));
    }

    @Test
    void shouldSummarizeAlloggiatiFailures() {
        final UUID summaryHotelId = Objects.requireNonNull(hotelId);
        final LocalDateTime now = LocalDateTime.now();
        final Stay olderFailure = Stay.builder()
                .id(UUID.randomUUID())
                .actualCheckInTime(now.minusDays(1))
                .alloggiatiSendFailed(true)
                .alloggiatiFailureReason("Token expired")
                .build();
        final Stay newerFailure = Stay.builder()
                .id(UUID.randomUUID())
                .actualCheckInTime(now)
                .alloggiatiSendFailed(true)
                .alloggiatiFailureReason(PS_PORTAL_DOWN)
                .build();

        when(stayRepository.findByHotelIdAndAlloggiatiSendFailedTrue(summaryHotelId))
                .thenReturn(List.of(olderFailure, newerFailure));

        final var summary = stayService.getAlloggiatiFailureSummary(summaryHotelId);

        assertEquals(2, summary.failedCount());
        assertEquals(newerFailure.getActualCheckInTime(), summary.mostRecentFailureAt());
        assertEquals(PS_PORTAL_DOWN, summary.mostRecentFailureReason());
    }

    @Test
    void shouldReturnZeroFailuresWhenNoneExist() {
        final UUID noFailuresHotelId = Objects.requireNonNull(hotelId);
        when(stayRepository.findByHotelIdAndAlloggiatiSendFailedTrue(noFailuresHotelId))
                .thenReturn(List.of());

        final var summary = stayService.getAlloggiatiFailureSummary(noFailuresHotelId);

        assertEquals(0, summary.failedCount());
        assertNull(summary.mostRecentFailureAt());
        assertNull(summary.mostRecentFailureReason());
    }

    @Test
    void shouldMarkRoomOccupiedOnCheckIn() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        saved.setHotelId(hotelId);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(UUID.randomUUID()));
        when(billingClient.addCharge(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert — OCCUPIED must be confirmed before invoice is opened (no orphan invoices)
        final InOrder sagaOrder = inOrder(roomService, billingClient);
        sagaOrder.verify(roomService).updateRoomStatus(room, hotelId, RoomStatus.OCCUPIED);
        sagaOrder.verify(billingClient).createInvoiceForStay(anyNonNull(StayInvoiceRequest.class));
    }

    @Test
    void shouldRollbackStayWhenRoomOccupiedFails() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(roomService.updateRoomStatus(room, null, RoomStatus.OCCUPIED))
                .thenThrow(new NotFoundException(ROOM_NOT_FOUND));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        // Act & Assert — exception propagates; @Transactional rolls back the Stay save in production
        assertThrows(NotFoundException.class, () -> stayService.checkIn(request));
        verifyNoInteractions(billingClient);
    }

    @Test
    void shouldContinueCheckInWhenReservationUpdateFails() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);
        final StayResponse expected = Objects.requireNonNull(validResponse);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        doThrow(new NotFoundException("RESERVATION_NOT_FOUND")).when(reservationService)
                .updateStatusAndGuests(ArgumentMatchers.eq(reservation), ArgumentMatchers.any(), ArgumentMatchers.any());

        // Act — Stay and room remain consistent even if the non-blocking reservation update fails
        final StayResponse response = stayService.checkIn(request);

        // Assert
        assertNotNull(response);
        verify(roomService, times(1)).updateRoomStatus(room, null, RoomStatus.OCCUPIED);
        verify(stayRepository, times(1)).save(Objects.requireNonNull(unmappedStay));
    }

    @Test
    void shouldReturnLastCompletedStayScopedToTheAuthenticatedHotel() {
        // Regression test for T-STAY-06: pre-fill must never return another hotel's stay.
        final Stay checkedOutStay = Objects.requireNonNull(savedStay);
        checkedOutStay.setStatus(StayStatus.CHECKED_OUT);

        when(guestClient.getGuestById(guestId))
                .thenReturn(new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(stayRepository.findTopByGuestIdAndHotelIdAndStatusOrderByActualCheckInTimeDesc(
                guestId, hotelId, StayStatus.CHECKED_OUT))
                .thenReturn(Optional.of(checkedOutStay));
        when(stayMapper.toDto(checkedOutStay)).thenReturn(Objects.requireNonNull(validResponse));

        final Optional<StayResponse> result = stayService.getLastCompletedStayForGuest(guestId, hotelId);

        assertTrue(result.isPresent());
        verify(stayRepository).findTopByGuestIdAndHotelIdAndStatusOrderByActualCheckInTimeDesc(
                guestId, hotelId, StayStatus.CHECKED_OUT);
    }

    @Test
    void shouldReturnEmptyWhenGuestServiceUnavailableForPreFill() {
        when(guestClient.getGuestById(guestId))
                .thenThrow(mock(feign.FeignException.NotFound.class));

        final Optional<StayResponse> result = stayService.getLastCompletedStayForGuest(guestId, hotelId);

        assertFalse(result.isPresent());
        verify(stayRepository, never())
                .findTopByGuestIdAndHotelIdAndStatusOrderByActualCheckInTimeDesc(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    // -----------------------------------------------------------------
    // extendStay (Parte 3)
    // -----------------------------------------------------------------

    @Test
    void extendStaySuccessfullyPostsRoomChargeAndRectifiesCityTax() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        final LocalDate oldCheckOut = stay.getExpectedCheckOutDate();
        final LocalDate newCheckOut = oldCheckOut.plusDays(2);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(new InvoiceStatusResponse(UUID.randomUUID(), reservationId, OPEN_STATUS, BigDecimal.ZERO));
        when(reservationService.isRoomBookedByOthers(roomId, oldCheckOut, newCheckOut)).thenReturn(false);
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(room());
        when(billingClient.addCharge(ArgumentMatchers.eq(id), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(validResponse);

        final StayResponse response = stayService.extendStay(id, hotelId, newCheckOut);

        assertNotNull(response);
        assertEquals(newCheckOut, stay.getExpectedCheckOutDate());
        verify(billingClient, times(1)).addCharge(ArgumentMatchers.eq(id), ArgumentMatchers.any());
        verify(cityTaxAssessmentService, times(1)).rectifyForStayExtended(stay, oldCheckOut, newCheckOut);
    }

    @Test
    void extendStayRejectsWhenRoomBookedByOthers() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        final LocalDate oldCheckOut = stay.getExpectedCheckOutDate();
        final LocalDate newCheckOut = oldCheckOut.plusDays(2);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(new InvoiceStatusResponse(UUID.randomUUID(), reservationId, OPEN_STATUS, BigDecimal.ZERO));
        when(reservationService.isRoomBookedByOthers(roomId, oldCheckOut, newCheckOut)).thenReturn(true);

        assertThrows(ConflictException.class, () -> stayService.extendStay(id, hotelId, newCheckOut));
        verify(billingClient, never()).addCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(cityTaxAssessmentService, never())
                .rectifyForStayExtended(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void extendStayRejectsWhenInvoiceNotOpen() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        final LocalDate newCheckOut = stay.getExpectedCheckOutDate().plusDays(2);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(new InvoiceStatusResponse(UUID.randomUUID(), reservationId, PAID_STATUS, BigDecimal.ZERO));

        assertThrows(ConflictException.class, () -> stayService.extendStay(id, hotelId, newCheckOut));
        verify(reservationService, never())
                .isRoomBookedByOthers(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void extendStayRejectsWhenNewDateNotAfterCurrentCheckout() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(BadRequestException.class,
                () -> stayService.extendStay(id, hotelId, stay.getExpectedCheckOutDate()));
    }

    @Test
    void extendStayRejectsWhenStayNotCheckedIn() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setHotelId(hotelId);
        stay.setStatus(StayStatus.CHECKED_OUT);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(IllegalStateException.class,
                () -> stayService.extendStay(id, hotelId, stay.getExpectedCheckOutDate().plusDays(1)));
    }

    @Test
    void extendStayThrowsNotFoundForUnknownStay() {
        final UUID id = Objects.requireNonNull(stayId);
        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> stayService.extendStay(id, hotelId, LocalDate.now().plusDays(1)));
    }

    /**
     * Null-safety bridge for Mockito's {@code any(Class)} matcher.
     *
     * <p>
     * {@code ArgumentMatchers.any(Class)} is annotated {@code @Nullable} in its
     * return type, but Spring Data's {@code save(@NonNull S)} parameter requires
     * {@code @NonNull}. This helper centralises the single
     * {@code @SuppressWarnings}
     * needed to bridge that gap, keeping every test method annotation-free.
     *
     * @param <T>  the type of the matcher
     * @param type the class token
     * @return a Mockito argument matcher of the requested type
     */
    @NonNull
    private static <T> T anyNonNull(final Class<T> type) {
        return ArgumentMatchers.any(type);
    }
}
