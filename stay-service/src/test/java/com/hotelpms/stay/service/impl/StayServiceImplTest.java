package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.client.GuestClient;
import com.hotelpms.stay.client.InventoryClient;
import com.hotelpms.stay.client.ReservationClient;
import com.hotelpms.stay.client.dto.GuestResponse;
import com.hotelpms.stay.client.dto.ReservationResponse;
import com.hotelpms.stay.client.dto.RoomResponse;
import com.hotelpms.stay.client.dto.ReservationLineItemResponse;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.domain.StayGuest;
import com.hotelpms.stay.domain.StayStatus;
import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import com.hotelpms.stay.exception.ExternalServiceException;
import com.hotelpms.stay.exception.NotFoundException;
import com.hotelpms.stay.mapper.StayMapper;
import com.hotelpms.stay.repository.StayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.mockito.ArgumentMatchers;
import org.springframework.lang.NonNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StayServiceImplTest {

    private static final String GUEST_FIRST_NAME = "John";
    private static final String GUEST_LAST_NAME = "Doe";
    private static final String GUEST_EMAIL = "john@example.com";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String ROOM_NUMBER_101 = "101";
    private static final String ROOM_STATUS_AVAILABLE = "AVAILABLE";

    @Mock
    private StayRepository stayRepository;

    @Mock
    private StayMapper stayMapper;

    @Mock
    private GuestClient guestClient;

    @Mock
    private ReservationClient reservationClient;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private StayServiceImpl stayService;

    private UUID stayId = UUID.randomUUID();
    private UUID guestId = UUID.randomUUID();
    private UUID reservationId = UUID.randomUUID();
    private UUID roomId = UUID.randomUUID();

    private StayRequest validRequest = new StayRequest(null, reservationId, guestId, roomId,
            StayStatus.EXPECTED, null, null, new ArrayList<>());
    private Stay savedStay = new Stay();
    private StayResponse validResponse;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        validRequest = new StayRequest(null, reservationId, guestId, roomId,
                StayStatus.EXPECTED, null, null, new ArrayList<>());

        savedStay = Stay.builder()
                .id(stayId)
                .reservationId(reservationId)
                .guestId(guestId)
                .roomId(roomId)
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .build();

        validResponse = new StayResponse(stayId, null, reservationId, guestId, roomId,
                StayStatus.CHECKED_IN, savedStay.getActualCheckInTime(), null,
                LocalDateTime.now(), LocalDateTime.now(), new ArrayList<>());
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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));

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
        verify(reservationClient, times(2)).getReservationById(reservation);
        verify(inventoryClient, times(1)).getRoomById(room);
        verify(stayRepository, times(1)).save(Objects.requireNonNull(unmappedStay));
        assertEquals(StayStatus.CHECKED_IN, unmappedStay.getStatus());
        assertNotNull(unmappedStay.getActualCheckInTime());
    }

    @Test
    void shouldAbortCheckInWhenInventoryServiceFails() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null));
        final feign.FeignException feignEx = org.mockito.Mockito.mock(feign.FeignException.class);
        when(feignEx.getMessage()).thenReturn("Inventory Service is unavailable.");
        when(inventoryClient.getRoomById(room)).thenThrow(feignEx);

        // Act & Assert
        final ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> stayService.checkIn(request));
        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE: Inventory Service is unavailable.",
                exception.getMessage());

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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));

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
                new ReservationLineItemResponse(UUID.randomUUID(), room, false, null, null);
        final ReservationLineItemResponse lineItem2 =
                new ReservationLineItemResponse(UUID.randomUUID(), UUID.randomUUID(), false, null, null);
        final List<ReservationLineItemResponse> lineItems = List.of(lineItem1, lineItem2);

        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, lineItems));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));

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
        verify(reservationClient, times(1)).updateStatusAndGuests(
            ArgumentMatchers.eq(reservation),
            ArgumentMatchers.argThat(req ->
                "PARTIALLY_CHECKED_IN".equals(req.status()) && req.actualGuests() == 1)
        );
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
                new ReservationLineItemResponse(UUID.randomUUID(), room, false, null, null);
        final List<ReservationLineItemResponse> lineItems = List.of(lineItem1);

        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, lineItems));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));

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
        verify(reservationClient, times(1)).updateStatusAndGuests(
            ArgumentMatchers.eq(reservation),
            ArgumentMatchers.argThat(req ->
                "CHECKED_IN".equals(req.status()) && req.actualGuests() == 2)
        );
    }

    @Test
    void shouldGetStayByIdSuccessfully() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final StayResponse expectedResponse = Objects.requireNonNull(validResponse);

        when(stayRepository.findById(id)).thenReturn(Optional.of(stay));
        when(stayMapper.toDto(stay)).thenReturn(expectedResponse);

        // Act
        final StayResponse response = stayService.getStayById(id);

        // Assert
        assertNotNull(response);
        assertEquals(id, response.id());
    }

    @Test
    void shouldThrowNotFoundWhenStayDoesNotExist() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);

        when(stayRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> stayService.getStayById(id));
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
    @SuppressWarnings("null")
    private static <T> T anyNonNull(final Class<T> type) {
        return ArgumentMatchers.any(type);
    }
}
