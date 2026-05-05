package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.client.BillingClient;
import com.hotelpms.stay.client.GuestClient;
import com.hotelpms.stay.client.InventoryClient;
import com.hotelpms.stay.client.ReservationClient;
import com.hotelpms.stay.client.dto.InvoiceCreatedResponse;
import com.hotelpms.stay.client.dto.InvoiceStatusResponse;
import com.hotelpms.stay.client.dto.StayInvoiceRequest;
import com.hotelpms.stay.exception.BillingNotPaidException;
import com.hotelpms.stay.client.dto.GuestResponse;
import com.hotelpms.stay.client.dto.ReservationResponse;
import com.hotelpms.stay.client.dto.RoomResponse;
import com.hotelpms.stay.client.dto.ReservationLineItemResponse;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.domain.StayGuest;
import com.hotelpms.stay.domain.StayStatus;
import com.hotelpms.stay.dto.HotelSettingsResponse;
import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import com.hotelpms.stay.exception.ExternalServiceException;
import com.hotelpms.stay.exception.NotFoundException;
import com.hotelpms.stay.mapper.StayMapper;
import com.hotelpms.stay.repository.StayRepository;
import com.hotelpms.stay.service.AlloggiatiWebSenderService;
import com.hotelpms.stay.service.HotelSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.lang.NonNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StayServiceImplTest {

    private static final String GUEST_FIRST_NAME = "John";
    private static final String GUEST_LAST_NAME = "Doe";
    private static final String GUEST_EMAIL = "john@example.com";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_CHECKED_OUT = "CHECKED_OUT";
    private static final String STATUS_NO_SHOW = "NO_SHOW";
    private static final String STATUS_PARTIALLY_CHECKED_IN = "PARTIALLY_CHECKED_IN";
    private static final String ROOM_NUMBER_101 = "101";
    private static final String ROOM_STATUS_AVAILABLE = "AVAILABLE";
    private static final String ROOM_STATUS_OCCUPIED = "OCCUPIED";

    @Mock
    private StayRepository stayRepository;

    @Mock
    private StayMapper stayMapper;

    @Mock
    private BillingClient billingClient;

    @Mock
    private GuestClient guestClient;

    @Mock
    private ReservationClient reservationClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private AlloggiatiWebSenderService alloggiatiWebSenderService;

    @Mock
    private HotelSettingsService hotelSettingsService;

    @InjectMocks
    private StayServiceImpl stayService;

    private UUID stayId = UUID.randomUUID();
    private UUID guestId = UUID.randomUUID();
    private UUID reservationId = UUID.randomUUID();
    private UUID roomId = UUID.randomUUID();

    private StayRequest validRequest = new StayRequest(null, reservationId, guestId, roomId,
            StayStatus.EXPECTED, null, null, null, new ArrayList<>());
    private Stay savedStay = new Stay();
    private StayResponse validResponse;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        validRequest = new StayRequest(null, reservationId, guestId, roomId,
                StayStatus.EXPECTED, null, null, null, new ArrayList<>());

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
                LocalDateTime.now(), LocalDateTime.now(), null, false, new ArrayList<>());
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
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

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
        verify(inventoryClient, times(1)).updateRoomStatus(room, ROOM_STATUS_OCCUPIED);
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
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
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
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

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
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, lineItems, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

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
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, lineItems, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

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

    @Test
    void shouldCheckOutSuccessfully() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);

        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                UUID.randomUUID(), reservationId, "PAID", BigDecimal.valueOf(200));

        when(stayRepository.findById(id)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(paidInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);

        // Act
        final StayResponse response = stayService.checkOut(id);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_OUT, checkedInStay.getStatus());
        assertNotNull(checkedInStay.getActualCheckOutTime());
        verify(inventoryClient, times(1)).updateRoomStatus(Objects.requireNonNull(roomId), "DIRTY");
    }

    @Test
    void shouldThrowWhenCheckOutBillingNotPaid() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setReservationId(reservationId);

        final InvoiceStatusResponse unpaidInvoice = new InvoiceStatusResponse(
                UUID.randomUUID(), reservationId, "ISSUED", BigDecimal.valueOf(200));

        when(stayRepository.findById(id)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getLatestInvoiceByReservation(Objects.requireNonNull(reservationId)))
                .thenReturn(unpaidInvoice);

        // Act & Assert
        assertThrows(BillingNotPaidException.class, () -> stayService.checkOut(id));
    }

    @Test
    void shouldThrowWhenCheckOutStayNotCheckedIn() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay notCheckedInStay = Stay.builder()
                .id(id)
                .status(StayStatus.EXPECTED)
                .build();

        when(stayRepository.findById(id)).thenReturn(Optional.of(notCheckedInStay));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkOut(id));
    }

    @Test
    void shouldRejectCheckInWhenReservationIsCancelled() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CANCELLED, null, null));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkIn(request));
        verify(stayRepository, times(0)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldRejectCheckInWhenReservationIsCheckedOut() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CHECKED_OUT, null, null));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> stayService.checkIn(request));
        verify(stayRepository, times(0)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldRejectCheckInWhenReservationIsNoShow() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_NO_SHOW, null, null));

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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_PARTIALLY_CHECKED_IN, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

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

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        final UUID invoiceId = UUID.randomUUID();
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(invoiceId));
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert
        verify(billingClient, times(1)).createInvoiceForStay(anyNonNull(StayInvoiceRequest.class));
        assertEquals(invoiceId, saved.getInvoiceId());
        verify(stayRepository, times(2)).save(anyNonNull(Stay.class));
    }

    @Test
    void shouldSendAlloggiatiAutomaticallyWhenAutoSendEnabled() {
        final UUID hotelId = UUID.randomUUID();
        final Stay stayWithHotel = Stay.builder()
                .id(stayId)
                .hotelId(hotelId)
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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(stayMapper.toEntity(request)).thenReturn(new Stay());
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(stayWithHotel);
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, true));
        when(stayMapper.toDto(stayWithHotel)).thenReturn(Objects.requireNonNull(validResponse));

        stayService.checkIn(request);

        final LocalDate expectedDate = stayWithHotel.getActualCheckInTime().toLocalDate();
        verify(alloggiatiWebSenderService, times(1)).submitReport(expectedDate);
        assertTrue(stayWithHotel.isAlloggiatiSent());
    }

    @Test
    void shouldSkipAlloggiatiWhenAutoSendDisabled() {
        final UUID hotelId = UUID.randomUUID();
        final Stay stayWithHotel = Stay.builder()
                .id(stayId)
                .hotelId(hotelId)
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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(stayMapper.toEntity(request)).thenReturn(new Stay());
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(stayWithHotel);
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, false));
        when(stayMapper.toDto(stayWithHotel)).thenReturn(Objects.requireNonNull(validResponse));

        stayService.checkIn(request);

        verifyNoInteractions(alloggiatiWebSenderService);
        assertFalse(stayWithHotel.isAlloggiatiSent());
    }

    @Test
    void shouldNotBlockCheckInWhenAlloggiatiSendFails() {
        final UUID hotelId = UUID.randomUUID();
        final Stay stayWithHotel = Stay.builder()
                .id(stayId)
                .hotelId(hotelId)
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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(stayMapper.toEntity(request)).thenReturn(new Stay());
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(stayWithHotel);
        when(hotelSettingsService.getOrCreate(hotelId))
                .thenReturn(new HotelSettingsResponse(hotelId, true));
        doThrow(new ExternalServiceException("PS portal down", null))
                .when(alloggiatiWebSenderService)
                .submitReport(ArgumentMatchers.any(LocalDate.class));
        when(stayMapper.toDto(stayWithHotel)).thenReturn(Objects.requireNonNull(validResponse));

        final StayResponse response = stayService.checkIn(request);

        assertNotNull(response);
        assertFalse(stayWithHotel.isAlloggiatiSent());
    }

    @Test
    void shouldMarkRoomOccupiedOnCheckIn() {
        // Arrange
        final UUID guest = Objects.requireNonNull(guestId);
        final UUID reservation = Objects.requireNonNull(reservationId);
        final UUID room = Objects.requireNonNull(roomId);
        final StayRequest request = Objects.requireNonNull(validRequest);
        final Stay saved = Objects.requireNonNull(savedStay);

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert — OCCUPIED must be confirmed before invoice is opened (no orphan invoices)
        final InOrder sagaOrder = inOrder(inventoryClient, billingClient);
        sagaOrder.verify(inventoryClient).updateRoomStatus(room, ROOM_STATUS_OCCUPIED);
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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenThrow(new ExternalServiceException("Inventory service unavailable"));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);

        // Act & Assert — exception propagates; @Transactional rolls back the Stay save in production
        assertThrows(ExternalServiceException.class, () -> stayService.checkIn(request));
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
        when(reservationClient.getReservationById(reservation))
                .thenReturn(new ReservationResponse(reservation, guest, room, STATUS_CONFIRMED, null, null));
        when(inventoryClient.getRoomById(room))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_AVAILABLE));
        when(inventoryClient.updateRoomStatus(room, ROOM_STATUS_OCCUPIED))
                .thenReturn(new RoomResponse(room, ROOM_NUMBER_101, ROOM_STATUS_OCCUPIED));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(expected);

        final feign.FeignException feignEx = org.mockito.Mockito.mock(feign.FeignException.class);
        when(feignEx.getMessage()).thenReturn("Reservation service unavailable");
        doThrow(feignEx).when(reservationClient)
                .updateStatusAndGuests(ArgumentMatchers.eq(reservation), ArgumentMatchers.any());

        // Act — Stay and room remain consistent even if reservation update fails
        final StayResponse response = stayService.checkIn(request);

        // Assert
        assertNotNull(response);
        verify(inventoryClient, times(1)).updateRoomStatus(room, ROOM_STATUS_OCCUPIED);
        verify(stayRepository, times(1)).save(Objects.requireNonNull(unmappedStay));
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
