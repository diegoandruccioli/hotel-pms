package com.hotelpms.frontdesk.reservations.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.GuestSearchPageResponse;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservedRoomCharge;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.mapper.ReservationMapper;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final ReservationStatus STATUS_CONFIRMED = ReservationStatus.CONFIRMED;
    private static final String ROOM_NUMBER_101 = "101";
    private static final UUID GUEST_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID HOTEL_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final int EXPECTED_GUESTS = 2;
    private static final long MATCHING_VERSION = 5L;
    private static final long CURRENT_VERSION_AFTER_ANOTHER_EDIT = 7L;
    private static final Reservation ANY_RESERVATION = new Reservation();
    private static final UUID ANY_UUID = Objects.requireNonNull(UUID.randomUUID());
    private static final String ERR_CHECKOUT_AFTER_CHECKIN = "CHECKOUT_MUST_BE_AFTER_CHECKIN";
    private static final String ROOM_UNAVAILABLE_DATES_MSG = "ROOM_UNAVAILABLE_DATES";
    private static final UUID ROOM_TYPE_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final BigDecimal PRICE_100 = BigDecimal.valueOf(100);
    private static final BigDecimal PRICE_120 = BigDecimal.valueOf(120);
    private static final BigDecimal PRICE_240 = BigDecimal.valueOf(240);
    private static final String QUERY_MARIO = "mario";
    private static final int GUEST_SEARCH_CAP = 200;

    private static final String GUEST_FIRST_NAME = "Test";
    private static final String GUEST_LAST_NAME = "Guest";
    private static final String GUEST_EMAIL = "test@example.com";
    private static final String FULL_NAME = "Test Guest";
    private static final String HOTEL_NAME_TEST = "Hotel Test";

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private RoomService roomService;

    @Mock
    private GuestClient guestClient;

    @Mock
    private HotelSettingsService hotelSettingsService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private RatePricingService ratePricingService;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @NonNull
    private ReservationRequest request = new ReservationRequest(GUEST_ID, 0, LocalDate.now(), LocalDate.now(),
            STATUS_CONFIRMED, List.of(), null);

    @NonNull
    private Reservation entity = new Reservation();

    @NonNull
    private ReservationResponse response = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null, 0, 0,
            LocalDate.now(), LocalDate.now(), STATUS_CONFIRMED, null, true, null, null, false, null, null);

    @NonNull
    private final UUID reservationId = Objects.requireNonNull(UUID.randomUUID());
    @NonNull
    private final UUID roomId = Objects.requireNonNull(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        final ReservationLineItemRequest lineItemRequest = new ReservationLineItemRequest(roomId);
        org.mockito.Mockito.lenient().when(ratePricingService.resolveStayRates(any(), any(), any(), any()))
                .thenReturn(List.of(new NightlyRate(LocalDate.now(), BigDecimal.valueOf(100), null)));
        request = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                STATUS_CONFIRMED,
                List.of(lineItemRequest), null);

        final ReservationLineItem lineItemEntity = new ReservationLineItem();
        lineItemEntity.setRoomId(roomId);
        lineItemEntity.setPrice(BigDecimal.valueOf(100));

        entity = Reservation.builder().id(reservationId).build();
        entity.setGuestId(GUEST_ID);
        entity.setExpectedGuests(EXPECTED_GUESTS);
        entity.setCheckInDate(LocalDate.now().plusDays(1));
        entity.setCheckOutDate(LocalDate.now().plusDays(3));
        entity.setStatus(STATUS_CONFIRMED);
        final List<ReservationLineItem> items = new ArrayList<>();
        items.add(lineItemEntity);
        entity.setLineItems(items);

        response = new ReservationResponse(reservationId, GUEST_ID, FULL_NAME, EXPECTED_GUESTS, 0,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                STATUS_CONFIRMED, null, true, null, null, false, null, null);
    }

    private static RoomTypeResponse roomType() {
        return new RoomTypeResponse(ROOM_TYPE_ID, "Standard", null, 2, BigDecimal.valueOf(100), true, null, null);
    }

    private static RoomResponse activeRoom(final UUID room) {
        return new RoomResponse(room, HOTEL_ID, ROOM_NUMBER_101, roomType(), RoomStatus.CLEAN, true, null, null, null);
    }

    private static RoomResponse inactiveRoom(final UUID room) {
        return new RoomResponse(room, HOTEL_ID, ROOM_NUMBER_101, roomType(), RoomStatus.CLEAN, false, null, null, null);
    }

    @Test
    void testCreateReservationSuccess() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(notificationClient.sendReservationConfirmed(any())).thenReturn(true);

        final ReservationResponse result = reservationService.createReservation(request);

        assertNotNull(result);
        assertEquals(GUEST_ID, result.guestId());
        assertFalse(entity.isConfirmationEmailFailed());
        verify(guestClient, times(1)).getGuestById(GUEST_ID);
        verify(roomService, times(1)).getRoomById(roomId, HOTEL_ID);
        verify(reservationRepository, times(1)).saveAndFlush(entity);
        verify(reservationRepository, times(1)).save(entity);
        verify(notificationClient, times(1)).sendReservationConfirmed(any());
    }

    @Test
    void testCreateReservationMarksEmailFailedWhenNotificationServiceUnavailable() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(notificationClient.sendReservationConfirmed(any())).thenReturn(false);

        reservationService.createReservation(request);

        assertTrue(entity.isConfirmationEmailFailed());
        assertEquals("NOTIFICATION_SERVICE_UNAVAILABLE", entity.getConfirmationEmailFailureReason());
    }

    @Test
    void createReservationFromPricedRoomsUsesTheGivenPriceNotRatePricingService() {
        final BigDecimal frozenPrice = BigDecimal.valueOf(310);
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(any(ReservationRequest.class))).thenReturn(entity);
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(notificationClient.sendReservationConfirmed(any())).thenReturn(true);

        final ReservationResponse result = reservationService.createReservationFromPricedRooms(
                GUEST_ID, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), EXPECTED_GUESTS,
                java.util.Map.of(roomId, frozenPrice));

        assertNotNull(result);
        assertEquals(frozenPrice, entity.getLineItems().get(0).getPrice());
    }

    @Test
    void testRetryConfirmationEmailClearsFailedFlag() {
        entity.setConfirmationEmailFailed(true);
        entity.setConfirmationEmailFailureReason("NOTIFICATION_SERVICE_UNAVAILABLE");

        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));
        when(notificationClient.sendReservationConfirmed(any())).thenReturn(true);
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        final ReservationResponse result = reservationService.retryConfirmationEmail(reservationId);

        assertNotNull(result);
        assertFalse(entity.isConfirmationEmailFailed());
        assertNull(entity.getConfirmationEmailFailureReason());
    }

    @Test
    void testRetryConfirmationEmailThrowsWhenReservationNotFound() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> reservationService.retryConfirmationEmail(reservationId));
    }

    @Test
    void testCreateReservationSkipsEmailWhenDisabledByHotelSettings() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        false, true, null, null, null, null, null, null, null));

        final ReservationResponse result = reservationService.createReservation(request);

        assertNotNull(result);
        verify(notificationClient, never()).sendReservationConfirmed(any());
    }

    @Test
    void testCreateReservationSetsDefaultStatusAndActualGuests() {
        final ReservationRequest requestWithNullStatus = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                null,
                List.of(new ReservationLineItemRequest(roomId)), null);

        final Reservation entityWithNullStatus = Reservation.builder().id(UUID.randomUUID()).build();
        entityWithNullStatus.setGuestId(GUEST_ID);
        entityWithNullStatus.setCheckInDate(LocalDate.now().plusDays(1));
        entityWithNullStatus.setCheckOutDate(LocalDate.now().plusDays(3));

        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(requestWithNullStatus)).thenReturn(entityWithNullStatus);
        when(reservationRepository.saveAndFlush(anyReservation()))
                .thenReturn(entityWithNullStatus);
        when(reservationMapper.toResponse(entityWithNullStatus)).thenReturn(response);
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME_TEST, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null, null));

        reservationService.createReservation(requestWithNullStatus);

        assertEquals(ReservationStatus.CONFIRMED, entityWithNullStatus.getStatus());
        assertEquals(0, entityWithNullStatus.getActualGuests());
    }

    @Test
    void testCreateReservationGuestNotFoundThrowsException() {
        final FeignException.NotFound notFoundEx = mock(FeignException.NotFound.class);
        when(guestClient.getGuestById(GUEST_ID)).thenThrow(notFoundEx);

        final BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> reservationService.createReservation(request));

        assertEquals("GUEST_NOT_FOUND", ex.getMessage());
        verify(roomService, never()).getRoomById(Objects.requireNonNullElse(any(UUID.class), ANY_UUID), any());
        verify(reservationRepository, never())
                .save(anyReservation());
    }

    @Test
    void testCreateReservationRoomUnavailableThrowsException() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(inactiveRoom(roomId));

        final ExternalServiceException ex = assertThrows(ExternalServiceException.class,
                () -> reservationService.createReservation(request));
        assertEquals("ROOM_UNAVAILABLE", ex.getMessage());

        verify(roomService, times(1)).getRoomById(roomId, HOTEL_ID);
        verify(reservationRepository, never()).save(entity);
    }

    @Test
    void testCreateReservationRoomNotFoundThrowsException() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenThrow(new NotFoundException("ROOM_NOT_FOUND"));

        final NotFoundException ex = assertThrows(NotFoundException.class,
                () -> reservationService.createReservation(request));
        assertEquals("ROOM_NOT_FOUND", ex.getMessage());

        verify(roomService, times(1)).getRoomById(roomId, HOTEL_ID);
        verify(reservationRepository, never()).save(entity);
    }

    @Test
    void testGetReservationByIdSuccess() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        final ReservationResponse result = reservationService.getReservationById(reservationId);

        assertNotNull(result);
        verify(reservationRepository, times(1)).findByIdAndHotelId(reservationId, HOTEL_ID);
    }

    @Test
    void testGetReservationByIdNotFoundThrowsException() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservationService.getReservationById(reservationId));
        verify(reservationRepository, times(1)).findByIdAndHotelId(reservationId, HOTEL_ID);
    }

    @Test
    void testGetReservationByIdCrossHotelReturnsNotFound() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservationService.getReservationById(reservationId));
    }

    @Test
    void testGetAllReservationsSuccess() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Reservation> reservationPage = new PageImpl<>(List.of(entity), pageable, 1L);
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);

        when(reservationRepository.findAllByHotelId(HOTEL_ID, pageable)).thenReturn(reservationPage);
        when(guestClient.getGuestsBatch(List.of(GUEST_ID))).thenReturn(List.of(mockGuestResponse));
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        final Page<ReservationResponse> result = reservationService.getAllReservations(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testGetAllReservationsEmpty() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Reservation> emptyPage = Page.empty(pageable);

        when(reservationRepository.findAllByHotelId(HOTEL_ID, pageable)).thenReturn(emptyPage);

        final Page<ReservationResponse> result = reservationService.getAllReservations(pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    // ---------------------------------------------------------------
    // searchReservations (C12)
    // ---------------------------------------------------------------

    @Test
    void testSearchReservationsWithNoQuerySkipsGuestResolution() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Reservation> reservationPage = new PageImpl<>(List.of(entity), pageable, 1L);
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);

        when(reservationRepository.searchReservationsByHotelId(HOTEL_ID, null, List.of(), pageable))
                .thenReturn(reservationPage);
        when(guestClient.getGuestsBatch(List.of(GUEST_ID))).thenReturn(List.of(mockGuestResponse));
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        final Page<ReservationResponse> result = reservationService.searchReservations(null, false, null, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(guestClient, never()).searchGuests(any(), anyInt());
    }

    @Test
    void testSearchReservationsWithBlankQueryIsTreatedAsNoQuery() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(reservationRepository.searchReservationsByHotelId(HOTEL_ID, null, List.of(), pageable))
                .thenReturn(Page.empty(pageable));

        reservationService.searchReservations("   ", false, null, null, null, pageable);

        verify(guestClient, never()).searchGuests(any(), anyInt());
    }

    @Test
    void testSearchReservationsWithQueryResolvesGuestIds() {
        final Pageable pageable = PageRequest.of(0, 20);
        final UUID otherGuestId = Objects.requireNonNull(UUID.randomUUID());
        final GuestResponse matched =
                new GuestResponse(otherGuestId, "Mario", "Rossi", "mario@test.com");

        when(guestClient.searchGuests(QUERY_MARIO, GUEST_SEARCH_CAP))
                .thenReturn(new GuestSearchPageResponse(List.of(matched)));
        when(reservationRepository.searchReservationsByHotelId(
                HOTEL_ID, QUERY_MARIO, List.of(otherGuestId), pageable))
                .thenReturn(Page.empty(pageable));

        reservationService.searchReservations("  " + QUERY_MARIO + "  ", false, null, null, null, pageable);

        verify(reservationRepository).searchReservationsByHotelId(
                HOTEL_ID, QUERY_MARIO, List.of(otherGuestId), pageable);
    }

    @Test
    void testSearchReservationsUpcomingOnlyAppliesTodayAsLowerBound() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(reservationRepository.searchUpcomingReservationsByHotelId(
                eq(HOTEL_ID), eq(LocalDate.now()), eq(null), eq(List.of()), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        reservationService.searchReservations(null, true, null, null, null, pageable);

        verify(reservationRepository).searchUpcomingReservationsByHotelId(
                eq(HOTEL_ID), eq(LocalDate.now()), eq(null), eq(List.of()), eq(pageable));
    }

    @Test
    void testSearchReservationsWithDateRangeUsesFilterQuery() {
        final Pageable pageable = PageRequest.of(0, 20);
        final LocalDate dateFrom = LocalDate.of(2026, 8, 1);
        final LocalDate dateTo = LocalDate.of(2026, 8, 31);
        when(reservationRepository.filterReservationsByHotelId(
                eq(HOTEL_ID), eq(dateFrom), eq(dateTo), any(), eq(null), eq(List.of()), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        reservationService.searchReservations(null, false, dateFrom, dateTo, null, pageable);

        verify(reservationRepository).filterReservationsByHotelId(
                eq(HOTEL_ID), eq(dateFrom), eq(dateTo), any(), eq(null), eq(List.of()), eq(pageable));
    }

    @Test
    void testSearchReservationsWithStatusFilterUsesFilterQueryWithSingleStatus() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(reservationRepository.filterReservationsByHotelId(
                eq(HOTEL_ID), any(), any(), eq(Set.of(ReservationStatus.CHECKED_IN)),
                eq(null), eq(List.of()), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        reservationService.searchReservations(null, false, null, null, ReservationStatus.CHECKED_IN, pageable);

        verify(reservationRepository).filterReservationsByHotelId(
                eq(HOTEL_ID), any(), any(), eq(Set.of(ReservationStatus.CHECKED_IN)),
                eq(null), eq(List.of()), eq(pageable));
    }

    @Test
    void testSearchReservationsDateRangeTakesPrecedenceOverUpcomingOnly() {
        final Pageable pageable = PageRequest.of(0, 20);
        final LocalDate dateFrom = LocalDate.of(2026, 8, 1);
        when(reservationRepository.filterReservationsByHotelId(
                eq(HOTEL_ID), eq(dateFrom), any(), any(), eq(null), eq(List.of()), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        reservationService.searchReservations(null, true, dateFrom, null, null, pageable);

        verify(reservationRepository, never()).searchUpcomingReservationsByHotelId(
                any(), any(), any(), any(), any());
    }

    @Test
    void testSearchReservationsEmptyResultSkipsGuestBatchResolution() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(reservationRepository.searchReservationsByHotelId(HOTEL_ID, null, List.of(), pageable))
                .thenReturn(Page.empty(pageable));

        final Page<ReservationResponse> result = reservationService.searchReservations(null, false, null, null, null, pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        verify(guestClient, never()).getGuestsBatch(any());
    }

    @Test
    void testUpdateReservationSuccess() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        // The real MapStruct mapper builds a new ReservationLineItem per request line
        // item (updateEntityFromRequest is deliberately excluded from touching
        // lineItems — see ReservationMapper's javadoc); reservationMapper is a plain
        // mock here, so stub toEntity(ReservationLineItemRequest) the same way the
        // other update test below does.
        when(reservationMapper.toEntity(any(ReservationLineItemRequest.class))).thenAnswer(invocation -> {
            final ReservationLineItemRequest lineItemRequest = invocation.getArgument(0);
            final ReservationLineItem newItem = new ReservationLineItem();
            newItem.setRoomId(lineItemRequest.roomId());
            return newItem;
        });

        final ReservationResponse result = reservationService.updateReservation(reservationId, request);

        assertNotNull(result);
        verify(reservationRepository, times(1)).findByIdAndHotelId(reservationId, HOTEL_ID);
        verify(roomService, times(1)).getRoomById(roomId, HOTEL_ID);
        verify(reservationMapper, times(1)).updateEntityFromRequest(request, entity);
        // Called twice: once to flush the old line items' removal before the
        // replacements are added (avoids a false-positive room-overlap conflict — see
        // ReservationServiceImpl#updateReservation's javadoc), once for the real save.
        verify(reservationRepository, times(2)).saveAndFlush(entity);
    }

    @Test
    void testUpdateReservationWithMatchingVersionSucceeds() {
        entity.setVersion(MATCHING_VERSION);
        final ReservationRequest requestWithMatchingVersion = new ReservationRequest(
                GUEST_ID, EXPECTED_GUESTS, request.checkInDate(), request.checkOutDate(),
                STATUS_CONFIRMED, request.lineItems(), MATCHING_VERSION);
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        when(reservationMapper.toEntity(any(ReservationLineItemRequest.class))).thenAnswer(invocation -> {
            final ReservationLineItemRequest lineItemRequest = invocation.getArgument(0);
            final ReservationLineItem newItem = new ReservationLineItem();
            newItem.setRoomId(lineItemRequest.roomId());
            return newItem;
        });

        final ReservationResponse result =
                reservationService.updateReservation(reservationId, requestWithMatchingVersion);

        assertNotNull(result);
        verify(reservationMapper, times(1)).updateEntityFromRequest(requestWithMatchingVersion, entity);
    }

    /**
     * The "forgotten tab" scenario (verifyNotStale's javadoc): a client that read the
     * reservation at version 5 must be rejected if it has since moved to version 7 —
     * mismatched, non-null client version is exactly what the staleness check exists
     * to catch. Also verifies the check runs before any other work (guest lookup, room
     * lookup) — a stale update should fail fast, not partly validate first.
     */
    @Test
    void testUpdateReservationWithStaleVersionThrowsConflictBeforeAnyOtherWork() {
        entity.setVersion(CURRENT_VERSION_AFTER_ANOTHER_EDIT);
        final ReservationRequest requestWithStaleVersion = new ReservationRequest(
                GUEST_ID, EXPECTED_GUESTS, request.checkInDate(), request.checkOutDate(),
                STATUS_CONFIRMED, request.lineItems(), MATCHING_VERSION);
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));

        assertThrows(ConflictException.class,
                () -> reservationService.updateReservation(reservationId, requestWithStaleVersion));

        verify(guestClient, never()).getGuestById(any());
        verify(roomService, never()).getRoomById(any(), any());
        verify(reservationMapper, never()).updateEntityFromRequest(any(), any());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void testUpdateReservationRecomputesPriceViaRatePricingServiceNotFromRequest() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(ratePricingService.resolveStayRates(ROOM_TYPE_ID, HOTEL_ID, request.checkInDate(), request.checkOutDate()))
                .thenReturn(List.of(
                        new NightlyRate(request.checkInDate(), PRICE_120, null),
                        new NightlyRate(request.checkInDate().plusDays(1), PRICE_120, null)));
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);
        // The real MapStruct mapper builds a new ReservationLineItem per request line
        // item — updateEntityFromRequest is deliberately excluded from touching
        // lineItems (see ReservationMapper's javadoc: pricing must happen on the
        // transient item BEFORE it joins the managed collection); reservationMapper is
        // a plain mock here, so stub toEntity(ReservationLineItemRequest) directly
        // instead of simulating the old behavior through updateEntityFromRequest.
        when(reservationMapper.toEntity(any(ReservationLineItemRequest.class))).thenAnswer(invocation -> {
            final ReservationLineItemRequest lineItemRequest = invocation.getArgument(0);
            final ReservationLineItem newItem = new ReservationLineItem();
            newItem.setRoomId(lineItemRequest.roomId());
            return newItem;
        });

        reservationService.updateReservation(reservationId, request);

        assertEquals(PRICE_240, entity.getLineItems().get(0).getPrice());
    }

    @Test
    void reservedRoomChargeIsTheSnapshottedPriceAndReservationNightsForTheMatchingRoom() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));

        final Optional<ReservedRoomCharge> result =
                reservationService.getReservedRoomCharge(reservationId, roomId, HOTEL_ID);

        assertTrue(result.isPresent());
        assertEquals(PRICE_100, result.get().price());
        // entity.checkInDate/checkOutDate (set in setUp) are 2 nights apart.
        assertEquals(2, result.get().nights());
    }

    @Test
    void reservedRoomChargeIsEmptyWhenReservationDoesNotExist() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.empty());

        assertFalse(reservationService.getReservedRoomCharge(reservationId, roomId, HOTEL_ID).isPresent());
    }

    @Test
    void reservedRoomChargeIsEmptyWhenNoLineItemMatchesTheRoom() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));

        final UUID otherRoomId = Objects.requireNonNull(UUID.randomUUID());
        assertFalse(reservationService.getReservedRoomCharge(reservationId, otherRoomId, HOTEL_ID).isPresent());
    }

    @Test
    void testDeleteReservationSuccess() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));

        reservationService.deleteReservation(reservationId);

        verify(reservationRepository, times(1)).findByIdAndHotelId(reservationId, HOTEL_ID);
        verify(reservationRepository, times(1)).delete(entity);
    }

    @Test
    void testDeleteReservationThrowsConflictWhenAlreadyCheckedIn() {
        entity.setStatus(ReservationStatus.CHECKED_IN);
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));

        assertThrows(ConflictException.class, () -> reservationService.deleteReservation(reservationId));

        verify(reservationRepository, never()).delete(any());
    }

    @Test
    void testHasActiveReservationsReturnsTrueWhenNonTerminalReservationExists() {
        when(reservationRepository.existsByGuestIdAndHotelIdAndStatusNotIn(
                GUEST_ID, HOTEL_ID, List.of(ReservationStatus.CHECKED_OUT, ReservationStatus.CANCELLED,
                        ReservationStatus.NO_SHOW)))
                .thenReturn(true);

        final boolean result = reservationService.hasActiveReservations(GUEST_ID);

        assertTrue(result);
    }

    @Test
    void testHasActiveReservationsReturnsFalseWhenOnlyTerminalReservationsExist() {
        when(reservationRepository.existsByGuestIdAndHotelIdAndStatusNotIn(
                GUEST_ID, HOTEL_ID, List.of(ReservationStatus.CHECKED_OUT, ReservationStatus.CANCELLED,
                        ReservationStatus.NO_SHOW)))
                .thenReturn(false);

        final boolean result = reservationService.hasActiveReservations(GUEST_ID);

        assertFalse(result);
    }

    @Test
    void testUpdateStatusAndGuestsSuccess() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        final ReservationResponse checkedInResponse = new ReservationResponse(
                reservationId, GUEST_ID, FULL_NAME, EXPECTED_GUESTS, 2,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.CHECKED_IN, null, true, null, null, false, null, null);

        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(reservationMapper.toResponse(entity)).thenReturn(checkedInResponse);

        final ReservationResponse result = reservationService.updateStatusAndGuests(
                reservationId, ReservationStatus.CHECKED_IN, 2);

        assertNotNull(result);
        assertEquals(ReservationStatus.CHECKED_IN, entity.getStatus());
        assertEquals(2, entity.getActualGuests());
        verify(reservationRepository).saveAndFlush(entity);
    }

    @Test
    void testUpdateStatusOnlyNullActualGuests() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        final ReservationResponse cancelledResponse = new ReservationResponse(
                reservationId, GUEST_ID, FULL_NAME, EXPECTED_GUESTS, 0,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.CANCELLED, null, true, null, null, false, null, null);

        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        when(reservationRepository.saveAndFlush(entity)).thenReturn(entity);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(reservationMapper.toResponse(entity)).thenReturn(cancelledResponse);

        reservationService.updateStatusAndGuests(reservationId, ReservationStatus.CANCELLED, null);

        assertEquals(ReservationStatus.CANCELLED, entity.getStatus());
        verify(reservationRepository).saveAndFlush(entity);
    }

    @Test
    void testUpdateStatusAndGuestsNotFound() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> reservationService.updateStatusAndGuests(
                        reservationId, ReservationStatus.CHECKED_IN, 1));
    }

    @Test
    void testCreateReservationOverlapThrowsBadRequest() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));

        final Reservation conflicting = new Reservation();
        conflicting.setCheckInDate(LocalDate.now().plusDays(1));
        conflicting.setCheckOutDate(LocalDate.now().plusDays(3));
        when(reservationRepository.findOverlappingReservationsForNew(
                List.of(roomId),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3)))
                .thenReturn(List.of(conflicting));

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(request));
        assertEquals(ROOM_UNAVAILABLE_DATES_MSG, ex.getMessage());
        verify(reservationRepository, never()).save(anyReservation());
    }

    @Test
    void testUpdateReservationOverlapThrowsBadRequest() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));

        final Reservation conflicting = new Reservation();
        conflicting.setCheckInDate(LocalDate.now().plusDays(1));
        conflicting.setCheckOutDate(LocalDate.now().plusDays(3));
        when(reservationRepository.findOverlappingReservations(
                List.of(roomId),
                reservationId,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3)))
                .thenReturn(List.of(conflicting));

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.updateReservation(reservationId, request));
        assertEquals(ROOM_UNAVAILABLE_DATES_MSG, ex.getMessage());
        verify(reservationRepository, never()).save(anyReservation());
    }

    @Test
    void testSaveThrowsOptimisticLockingFailurePropagates() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationRepository.findOverlappingReservationsForNew(any(), any(), any()))
                .thenReturn(List.of());
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.saveAndFlush(entity))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reservation.class,
                        Objects.requireNonNull(UUID.randomUUID())));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> reservationService.createReservation(request));
    }

    @Test
    void createReservationTranslatesResidualOverlapIntoConflict() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationRepository.findOverlappingReservationsForNew(any(), any(), any()))
                .thenReturn(List.of());
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.saveAndFlush(entity)).thenThrow(exclusionViolation());

        final ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.createReservation(request),
                "A concurrent request winning the DB-level race must surface a 409, not a raw 500");
        assertEquals(ROOM_UNAVAILABLE_DATES_MSG, ex.getMessage());
    }

    private static DataIntegrityViolationException exclusionViolation() {
        final SQLException sqlException = new SQLException("overlap", "23P01");
        return new DataIntegrityViolationException("excl_reservation_line_items_no_overlap", sqlException);
    }

    @Test
    void shouldRejectCreateWhenCheckOutSameDayAsCheckIn() {
        final ReservationRequest sameDayRequest = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1),
                STATUS_CONFIRMED,
                List.of(new ReservationLineItemRequest(roomId)), null);

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(sameDayRequest));
        assertEquals(ERR_CHECKOUT_AFTER_CHECKIN, ex.getMessage());
        verify(guestClient, never()).getGuestById(any());
        verify(reservationRepository, never()).save(anyReservation());
    }

    @Test
    void shouldRejectCreateWhenCheckOutBeforeCheckIn() {
        final ReservationRequest invertedRequest = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(2),
                STATUS_CONFIRMED,
                List.of(new ReservationLineItemRequest(roomId)), null);

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(invertedRequest));
        assertEquals(ERR_CHECKOUT_AFTER_CHECKIN, ex.getMessage());
        verify(guestClient, never()).getGuestById(any());
        verify(reservationRepository, never()).save(anyReservation());
    }

    @Test
    void shouldRejectUpdateWhenCheckOutSameDayAsCheckIn() {
        final ReservationRequest sameDayRequest = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(3),
                STATUS_CONFIRMED,
                List.of(new ReservationLineItemRequest(roomId)), null);

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.updateReservation(reservationId, sameDayRequest));
        assertEquals(ERR_CHECKOUT_AFTER_CHECKIN, ex.getMessage());
        verify(reservationRepository, never()).findByIdAndHotelId(any(), any());
        verify(reservationRepository, never()).save(anyReservation());
    }

    @NonNull
    private static Reservation anyReservation() {
        final Reservation matched = any(Reservation.class);
        return matched != null ? matched : Objects.requireNonNull(ANY_RESERVATION);
    }

    @Test
    void shouldReturnAllCleanRoomsWhenNoneAreBooked() {
        final UUID room1 = Objects.requireNonNull(UUID.randomUUID());
        final UUID room2 = Objects.requireNonNull(UUID.randomUUID());
        final LocalDate checkIn = LocalDate.now();
        final LocalDate checkOut = checkIn.plusDays(1);

        when(roomService.findCleanRooms(HOTEL_ID)).thenReturn(List.of(activeRoom(room1), activeRoom(room2)));
        when(reservationRepository.findOverlappingRoomIds(List.of(room1, room2), checkIn, checkOut))
                .thenReturn(List.of());

        final List<RoomResponse> result = reservationService.getAvailableRooms(checkIn, checkOut);

        assertEquals(2, result.size());
    }

    @Test
    void shouldExcludeRoomsWithOverlappingReservation() {
        final UUID freeRoom = Objects.requireNonNull(UUID.randomUUID());
        final UUID bookedRoom = Objects.requireNonNull(UUID.randomUUID());
        final LocalDate checkIn = LocalDate.now();
        final LocalDate checkOut = checkIn.plusDays(1);

        when(roomService.findCleanRooms(HOTEL_ID)).thenReturn(List.of(activeRoom(freeRoom), activeRoom(bookedRoom)));
        when(reservationRepository.findOverlappingRoomIds(List.of(freeRoom, bookedRoom), checkIn, checkOut))
                .thenReturn(List.of(bookedRoom));

        final List<RoomResponse> result = reservationService.getAvailableRooms(checkIn, checkOut);

        assertEquals(1, result.size());
        assertEquals(freeRoom, result.get(0).id());
    }

    @Test
    void shouldSkipOverlapQueryWhenNoCleanRoomsExist() {
        final LocalDate checkIn = LocalDate.now();
        final LocalDate checkOut = checkIn.plusDays(1);

        when(roomService.findCleanRooms(HOTEL_ID)).thenReturn(List.of());

        final List<RoomResponse> result = reservationService.getAvailableRooms(checkIn, checkOut);

        assertTrue(result.isEmpty());
        verify(reservationRepository, never()).findOverlappingRoomIds(any(), any(), any());
    }

    @Test
    void shouldRejectAvailabilityQueryWhenCheckOutNotAfterCheckIn() {
        final LocalDate sameDay = LocalDate.now();

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.getAvailableRooms(sameDay, sameDay));
        assertEquals(ERR_CHECKOUT_AFTER_CHECKIN, ex.getMessage());
        verify(roomService, never()).findCleanRooms(any());
    }
}
