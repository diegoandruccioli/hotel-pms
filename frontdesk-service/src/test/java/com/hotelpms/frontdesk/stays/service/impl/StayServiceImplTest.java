package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import com.hotelpms.frontdesk.exception.BillingNotPaidException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.mapper.StayMapper;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
import static org.junit.jupiter.api.Assertions.assertNull;
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

/**
 * Unit tests for {@link StayServiceImpl}.
 *
 * <p>Room and reservation lookups/updates are now in-process calls to
 * {@link RoomService} / {@link ReservationService} (formerly Feign clients to
 * inventory-service / reservation-service — ADR-001). Guest and billing remain
 * Feign ({@link GuestClient} / {@link BillingClient}).
 */
@ExtendWith(MockitoExtension.class)
class StayServiceImplTest {

    private static final String GUEST_FIRST_NAME = "John";
    private static final String GUEST_LAST_NAME = "Doe";
    private static final String GUEST_EMAIL = "john@example.com";
    private static final String ROOM_NUMBER_101 = "101";
    private static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    private static final String PS_PORTAL_DOWN = "PS portal down";
    private static final String PAID_STATUS = "PAID";

    @Mock
    private StayRepository stayRepository;

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

    @InjectMocks
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
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .build();

        validResponse = new StayResponse(stayId, null, reservationId, guestId, roomId,
                StayStatus.CHECKED_IN, savedStay.getActualCheckInTime(), null,
                LocalDateTime.now(), LocalDateTime.now(), null, false, false, null, new ArrayList<>(), null, null);
    }

    private ReservationResponse reservationResponse(
            final ReservationStatus status, final List<ReservationLineItemResponse> lineItems) {
        return new ReservationResponse(reservationId, guestId, null, 2, 0,
                LocalDate.now(), LocalDate.now().plusDays(3), status, lineItems, true, null, null);
    }

    private RoomResponse room() {
        return new RoomResponse(roomId, hotelId, ROOM_NUMBER_101, null, RoomStatus.CLEAN, true, null, null);
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
    void shouldCheckOutSuccessfully() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);

        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                UUID.randomUUID(), reservationId, PAID_STATUS, BigDecimal.valueOf(200));
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

        // Act
        final StayResponse response = stayService.checkOut(id, hotelId);

        // Assert
        assertNotNull(response);
        assertEquals(StayStatus.CHECKED_OUT, checkedInStay.getStatus());
        assertNotNull(checkedInStay.getActualCheckOutTime());
        verify(roomService, times(1)).updateRoomStatus(Objects.requireNonNull(roomId), null, RoomStatus.DIRTY);
        verify(reservationService, times(1))
                .updateStatusAndGuests(reservationId, ReservationStatus.CHECKED_OUT, null);
    }

    @Test
    void shouldNotCheckOutReservationWhenOtherRoomsStillCheckedIn() {
        // Arrange
        final UUID id = Objects.requireNonNull(stayId);
        final Stay checkedInStay = Objects.requireNonNull(savedStay);
        checkedInStay.setRoomId(roomId);
        checkedInStay.setReservationId(reservationId);

        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                UUID.randomUUID(), reservationId, PAID_STATUS, BigDecimal.valueOf(200));
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

        final InvoiceStatusResponse paidInvoice = new InvoiceStatusResponse(
                invoiceId, null, PAID_STATUS, BigDecimal.valueOf(80));

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(checkedInStay));
        when(billingClient.getInvoiceById(invoiceId)).thenReturn(paidInvoice);
        when(stayRepository.save(checkedInStay)).thenReturn(checkedInStay);
        when(stayMapper.toDto(checkedInStay)).thenReturn(validResponse);

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
                .thenReturn(new HotelSettingsResponse(stayHotelId, true, null, null, null, null, null));
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
                .thenReturn(new HotelSettingsResponse(stayHotelId, false, null, null, null, null, null));
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
                .thenReturn(new HotelSettingsResponse(stayHotelId, true, null, null, null, null, null));
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

        when(guestClient.getGuestById(guest))
                .thenReturn(new GuestResponse(guest, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(reservationService.getReservationById(reservation))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED, null));
        when(roomService.getRoomById(room, hotelId)).thenReturn(room());
        when(billingClient.createInvoiceForStay(anyNonNull(StayInvoiceRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(UUID.randomUUID()));

        final Stay unmappedStay = new Stay();
        when(stayMapper.toEntity(request)).thenReturn(unmappedStay);
        when(stayRepository.save(anyNonNull(Stay.class))).thenReturn(saved);
        when(stayMapper.toDto(saved)).thenReturn(Objects.requireNonNull(validResponse));

        // Act
        stayService.checkIn(request);

        // Assert — OCCUPIED must be confirmed before invoice is opened (no orphan invoices)
        final InOrder sagaOrder = inOrder(roomService, billingClient);
        sagaOrder.verify(roomService).updateRoomStatus(room, null, RoomStatus.OCCUPIED);
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
