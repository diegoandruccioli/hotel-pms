package com.hotelpms.frontdesk.reservations.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.mapper.ReservationMapper;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private static final Reservation ANY_RESERVATION = new Reservation();
    private static final UUID ANY_UUID = Objects.requireNonNull(UUID.randomUUID());
    private static final String ERR_CHECKOUT_AFTER_CHECKIN = "CHECKOUT_MUST_BE_AFTER_CHECKIN";

    private static final String GUEST_FIRST_NAME = "Test";
    private static final String GUEST_LAST_NAME = "Guest";
    private static final String GUEST_EMAIL = "test@example.com";
    private static final String FULL_NAME = "Test Guest";

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private RoomService roomService;

    @Mock
    private GuestClient guestClient;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @NonNull
    private ReservationRequest request = new ReservationRequest(GUEST_ID, 0, LocalDate.now(), LocalDate.now(),
            STATUS_CONFIRMED, List.of());

    @NonNull
    private Reservation entity = new Reservation();

    @NonNull
    private ReservationResponse response = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null, 0, 0,
            LocalDate.now(), LocalDate.now(), STATUS_CONFIRMED, null, true, null, null);

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

        final ReservationLineItemRequest lineItemRequest = new ReservationLineItemRequest(roomId,
                BigDecimal.valueOf(100));
        request = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                STATUS_CONFIRMED,
                List.of(lineItemRequest));

        final ReservationLineItem lineItemEntity = new ReservationLineItem();
        lineItemEntity.setRoomId(roomId);
        lineItemEntity.setPrice(BigDecimal.valueOf(100));

        entity = new Reservation();
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
                STATUS_CONFIRMED, null, true, null, null);
    }

    private static RoomResponse activeRoom(final UUID room) {
        return new RoomResponse(room, HOTEL_ID, ROOM_NUMBER_101, null, RoomStatus.CLEAN, true, null, null);
    }

    private static RoomResponse inactiveRoom(final UUID room) {
        return new RoomResponse(room, HOTEL_ID, ROOM_NUMBER_101, null, RoomStatus.CLEAN, false, null, null);
    }

    @Test
    void testCreateReservationSuccess() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        final ReservationResponse result = reservationService.createReservation(request);

        assertNotNull(result);
        assertEquals(GUEST_ID, result.guestId());
        verify(guestClient, times(1)).getGuestById(GUEST_ID);
        verify(roomService, times(1)).getRoomById(roomId, HOTEL_ID);
        verify(reservationRepository, times(1)).save(entity);
    }

    @Test
    void testCreateReservationSetsDefaultStatusAndActualGuests() {
        final ReservationRequest requestWithNullStatus = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                null,
                List.of(new ReservationLineItemRequest(roomId, BigDecimal.valueOf(100))));

        final Reservation entityWithNullStatus = new Reservation();
        entityWithNullStatus.setGuestId(GUEST_ID);

        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationMapper.toEntity(requestWithNullStatus)).thenReturn(entityWithNullStatus);
        when(reservationRepository.save(anyReservation()))
                .thenReturn(entityWithNullStatus);
        when(reservationMapper.toResponse(entityWithNullStatus)).thenReturn(response);

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

    @Test
    void testUpdateReservationSuccess() {
        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(roomService.getRoomById(roomId, HOTEL_ID)).thenReturn(activeRoom(roomId));
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        final ReservationResponse result = reservationService.updateReservation(reservationId, request);

        assertNotNull(result);
        verify(reservationRepository, times(1)).findByIdAndHotelId(reservationId, HOTEL_ID);
        verify(roomService, times(1)).getRoomById(roomId, HOTEL_ID);
        verify(reservationMapper, times(1)).updateEntityFromRequest(request, entity);
        verify(reservationRepository, times(1)).save(entity);
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
                ReservationStatus.CHECKED_IN, null, true, null, null);

        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(reservationMapper.toResponse(entity)).thenReturn(checkedInResponse);

        final ReservationResponse result = reservationService.updateStatusAndGuests(
                reservationId, ReservationStatus.CHECKED_IN, 2);

        assertNotNull(result);
        assertEquals(ReservationStatus.CHECKED_IN, entity.getStatus());
        assertEquals(2, entity.getActualGuests());
        verify(reservationRepository).save(entity);
    }

    @Test
    void testUpdateStatusOnlyNullActualGuests() {
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        final ReservationResponse cancelledResponse = new ReservationResponse(
                reservationId, GUEST_ID, FULL_NAME, EXPECTED_GUESTS, 0,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.CANCELLED, null, true, null, null);

        when(reservationRepository.findByIdAndHotelId(reservationId, HOTEL_ID)).thenReturn(Optional.of(entity));
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(reservationMapper.toResponse(entity)).thenReturn(cancelledResponse);

        reservationService.updateStatusAndGuests(reservationId, ReservationStatus.CANCELLED, null);

        assertEquals(ReservationStatus.CANCELLED, entity.getStatus());
        verify(reservationRepository).save(entity);
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
        assertEquals("ROOM_UNAVAILABLE_DATES", ex.getMessage());
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
        assertEquals("ROOM_UNAVAILABLE_DATES", ex.getMessage());
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
        when(reservationRepository.save(entity))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reservation.class,
                        Objects.requireNonNull(UUID.randomUUID())));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> reservationService.createReservation(request));
    }

    @Test
    void shouldRejectCreateWhenCheckOutSameDayAsCheckIn() {
        final ReservationRequest sameDayRequest = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1),
                STATUS_CONFIRMED,
                List.of(new ReservationLineItemRequest(roomId, BigDecimal.valueOf(100))));

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
                List.of(new ReservationLineItemRequest(roomId, BigDecimal.valueOf(100))));

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
                List.of(new ReservationLineItemRequest(roomId, BigDecimal.valueOf(100))));

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
}
