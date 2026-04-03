package com.hotelpms.reservation.service.impl;

import com.hotelpms.reservation.client.GuestClient;
import com.hotelpms.reservation.client.InventoryClient;
import com.hotelpms.reservation.client.dto.RoomResponse;
import com.hotelpms.reservation.domain.Reservation;
import com.hotelpms.reservation.domain.ReservationLineItem;
import com.hotelpms.reservation.domain.ReservationStatus;
import com.hotelpms.reservation.dto.ReservationLineItemRequest;
import com.hotelpms.reservation.dto.ReservationRequest;
import com.hotelpms.reservation.dto.ReservationResponse;
import com.hotelpms.reservation.client.dto.GuestResponse;
import com.hotelpms.reservation.exception.BadRequestException;
import com.hotelpms.reservation.exception.ExternalServiceException;
import com.hotelpms.reservation.exception.NotFoundException;
import com.hotelpms.reservation.mapper.ReservationMapper;
import com.hotelpms.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;

import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final ReservationStatus STATUS_CONFIRMED = ReservationStatus.CONFIRMED;
    private static final String ROOM_NUMBER_101 = "101";
    private static final String ROOM_STATUS_AVAILABLE = "AVAILABLE";
    private static final UUID GUEST_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final int EXPECTED_GUESTS = 2;
    private static final Reservation ANY_RESERVATION = new Reservation();
    private static final UUID ANY_UUID = Objects.requireNonNull(UUID.randomUUID());

    private static final String GUEST_FIRST_NAME = "Test";
    private static final String GUEST_LAST_NAME = "Guest";
    private static final String GUEST_EMAIL = "test@example.com";
    private static final String FULL_NAME = "Test Guest";

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private InventoryClient inventoryClient;

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

    @Test
    void testCreateReservationSuccess() {
        // Arrange
        final RoomResponse mockRoomResponse = new RoomResponse(roomId, ROOM_NUMBER_101, null, ROOM_STATUS_AVAILABLE,
                true, null, null);
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(inventoryClient.getRoomById(roomId)).thenReturn(Optional.of(mockRoomResponse));
        when(reservationMapper.toEntity(request)).thenReturn(entity);
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        // Act
        final ReservationResponse result = reservationService.createReservation(request);

        // Assert
        assertNotNull(result);
        assertEquals(GUEST_ID, result.guestId());
        verify(guestClient, times(1)).getGuestById(GUEST_ID);
        verify(inventoryClient, times(1)).getRoomById(roomId);
        verify(reservationRepository, times(1)).save(entity);
    }

    @Test
    void testCreateReservationSetsDefaultStatusAndActualGuests() {
        // Arrange
        final ReservationRequest requestWithNullStatus = new ReservationRequest(
                GUEST_ID,
                EXPECTED_GUESTS,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                null,
                List.of(new ReservationLineItemRequest(roomId, BigDecimal.valueOf(100))));

        final Reservation entityWithNullStatus = new Reservation();
        entityWithNullStatus.setGuestId(GUEST_ID);

        final RoomResponse mockRoomResponse = new RoomResponse(roomId, ROOM_NUMBER_101, null, ROOM_STATUS_AVAILABLE,
                true, null, null);
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(inventoryClient.getRoomById(roomId)).thenReturn(Optional.of(mockRoomResponse));
        when(reservationMapper.toEntity(requestWithNullStatus)).thenReturn(entityWithNullStatus);
        when(reservationRepository.save(anyReservation()))
                .thenReturn(entityWithNullStatus);
        when(reservationMapper.toResponse(entityWithNullStatus)).thenReturn(response);

        // Act
        reservationService.createReservation(requestWithNullStatus);

        // Assert
        assertEquals(ReservationStatus.CONFIRMED, entityWithNullStatus.getStatus());
        assertEquals(0, entityWithNullStatus.getActualGuests());
    }

    @Test
    void testCreateReservationGuestNotFoundThrowsException() {
        // Arrange
        final FeignException.NotFound notFoundEx = mock(FeignException.NotFound.class);
        when(guestClient.getGuestById(GUEST_ID)).thenThrow(notFoundEx);

        // Act & Assert
        final BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> reservationService.createReservation(request));

        assertEquals("GUEST_NOT_FOUND", ex.getMessage());
        verify(inventoryClient, never()).getRoomById(Objects.requireNonNullElse(any(UUID.class), ANY_UUID));
        verify(reservationRepository, never())
                .save(anyReservation());
    }

    @Test
    void testCreateReservationRoomUnavailableThrowsException() {
        // Arrange
        final RoomResponse mockRoomResponse = new RoomResponse(roomId, ROOM_NUMBER_101, null, "UNAVAILABLE",
                true, null, null);
        when(inventoryClient.getRoomById(roomId)).thenReturn(Optional.of(mockRoomResponse));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);

        // Act & Assert
        final ExternalServiceException ex = assertThrows(ExternalServiceException.class,
                () -> reservationService.createReservation(request));
        assertEquals("ROOM_UNAVAILABLE", ex.getMessage());

        verify(inventoryClient, times(1)).getRoomById(roomId);
        verify(reservationRepository, never()).save(entity);
    }

    @Test
    void testCreateReservationInventoryClientUnavailableThrowsException() {
        // Arrange: simulate the Resilience4j fallback returning Optional.empty()
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(inventoryClient.getRoomById(roomId)).thenReturn(Optional.empty());

        // Act & Assert: empty Optional → NotFoundException (room not retrievable from
        // inventory)
        final NotFoundException ex = assertThrows(NotFoundException.class,
                () -> reservationService.createReservation(request));
        assertEquals("ROOM_NOT_FOUND", ex.getMessage());

        verify(inventoryClient, times(1)).getRoomById(roomId);
        verify(reservationRepository, never()).save(entity);
    }

    @Test
    void testGetReservationByIdSuccess() {
        // Arrange
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(entity));
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        // Act
        final ReservationResponse result = reservationService.getReservationById(reservationId);

        // Assert
        assertNotNull(result);
        verify(reservationRepository, times(1)).findById(reservationId);
    }

    @Test
    void testGetReservationByIdNotFoundThrowsException() {
        // Arrange
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> reservationService.getReservationById(reservationId));
        verify(reservationRepository, times(1)).findById(reservationId);
    }

    @Test
    void testUpdateReservationSuccess() {
        // Arrange
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(entity));
        final RoomResponse mockRoomResponse = new RoomResponse(roomId, ROOM_NUMBER_101, null, ROOM_STATUS_AVAILABLE,
                true, null, null);
        final GuestResponse mockGuestResponse =
                new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(mockGuestResponse);
        when(inventoryClient.getRoomById(roomId)).thenReturn(Optional.of(mockRoomResponse));
        when(reservationRepository.save(entity)).thenReturn(entity);
        when(reservationMapper.toResponse(entity)).thenReturn(response);

        // Act
        final ReservationResponse result = reservationService.updateReservation(reservationId, request);

        // Assert
        assertNotNull(result);
        verify(reservationRepository, times(1)).findById(reservationId);
        verify(inventoryClient, times(1)).getRoomById(roomId);
        verify(reservationMapper, times(1)).updateEntityFromRequest(request, entity);
        verify(reservationRepository, times(1)).save(entity);
    }

    @Test
    void testDeleteReservationSuccess() {
        // Arrange
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(entity));

        // Act
        reservationService.deleteReservation(reservationId);

        // Assert
        verify(reservationRepository, times(1)).findById(reservationId);
        verify(reservationRepository, times(1)).delete(entity);
    }

    @NonNull
    private static Reservation anyReservation() {
        any(Reservation.class); // registers the Mockito argument matcher
        return Objects.requireNonNull(ANY_RESERVATION);
    }
}
