package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GatewayEventsClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StayServiceImpl#changeRoom} (Parte 6) — split out from
 * {@code StayServiceImplTest} (which was already at the file-length limit) into its
 * own file, same construction pattern: real {@link StayBillingCoordinator} /
 * {@link StayCheckInValidator} / {@link StayReservationSync} wired from leaf mocks,
 * not mocked themselves, so a test verifying e.g. {@code billingClient.addCharge}
 * exercises the actual repricing logic two hops down.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StayServiceImplRoomChangeTest {

    private static final String ROOM_NUMBER_101 = "101";
    private static final String NEW_ROOM_NUMBER_205 = "205";
    private static final String THIRD_ROOM_NUMBER_301 = "301";
    private static final String OPEN_STATUS = "ISSUED";
    private static final String PAID_STATUS = "PAID";
    private static final int STANDARD_MAX_OCCUPANCY = 2;
    private static final int SINGLE_MAX_OCCUPANCY = 1;
    private static final BigDecimal ROOM_CHARGE_UNIT_PRICE = BigDecimal.valueOf(80);
    private static final int ROOM_CHARGE_ORIGINAL_NIGHTS = 5;
    private static final int CONSUMED_NIGHTS = 2;
    private static final long STALE_CURRENT_VERSION = 7L;
    private static final long STALE_CLIENT_VERSION = 5L;

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
    private UUID reservationId;
    private UUID roomId;
    private UUID hotelId;

    private Stay savedStay;
    private StayResponse validResponse;

    @BeforeEach
    void setUp() {
        stayId = Objects.requireNonNull(UUID.randomUUID());
        reservationId = Objects.requireNonNull(UUID.randomUUID());
        roomId = Objects.requireNonNull(UUID.randomUUID());
        hotelId = Objects.requireNonNull(UUID.randomUUID());

        savedStay = Stay.builder()
                .id(stayId)
                .hotelId(hotelId)
                .reservationId(reservationId)
                .guestId(UUID.randomUUID())
                .roomId(roomId)
                .roomNumber(ROOM_NUMBER_101)
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .expectedCheckOutDate(LocalDate.now().plusDays(3))
                .build();

        validResponse = new StayResponse(stayId, null, reservationId, savedStay.getGuestId(), roomId,
                StayStatus.CHECKED_IN, savedStay.getActualCheckInTime(), null,
                LocalDateTime.now(), LocalDateTime.now(), null, false, false, null, new ArrayList<>(), null, null,
                null, false, null, false, null, null, null);

        lenient()
                .when(ratePricingService.resolveStayRates(ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final LocalDate from = invocation.getArgument(2);
                    final LocalDate to = invocation.getArgument(3);
                    final long nights = Math.max(1, ChronoUnit.DAYS.between(from, to));
                    final List<NightlyRate> rates = new ArrayList<>();
                    for (long i = 0; i < nights; i++) {
                        rates.add(new NightlyRate(from.plusDays(i), BigDecimal.valueOf(90), null));
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
    }

    private RoomResponse roomWithType(
            final UUID id, final String number, final UUID roomTypeId, final int maxOccupancy,
            final RoomStatus status) {
        final RoomTypeResponse roomType = new RoomTypeResponse(
                roomTypeId, "Type", null, maxOccupancy, BigDecimal.valueOf(90), true, null, null);
        return new RoomResponse(id, hotelId, number, roomType, status, true, null, null, null);
    }

    @Test
    void changeRoomSameRoomTypeSkipsBillingAndSyncsReservationAndHousekeeping() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final UUID sharedRoomTypeId = Objects.requireNonNull(UUID.randomUUID());
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());
        final RoomResponse newRoom =
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, sharedRoomTypeId, STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN);
        final RoomResponse oldRoom =
                roomWithType(roomId, ROOM_NUMBER_101, sharedRoomTypeId, STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(newRoom);
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(oldRoom);
        when(reservationService.isRoomBookedByOthers(newRoomId, LocalDate.now(), stay.getExpectedCheckOutDate()))
                .thenReturn(false);
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(validResponse);

        final StayResponse response = stayService.changeRoom(id, hotelId, newRoomId, null);

        assertNotNull(response);
        assertEquals(newRoomId, stay.getRoomId());
        assertEquals(NEW_ROOM_NUMBER_205, stay.getRoomNumber());
        verify(billingClient, never()).removeCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(billingClient, never()).addCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(reservationService, times(1))
                .syncLineItemRoomForCheckedInStay(reservationId, roomId, newRoomId, hotelId);
        verify(roomService, times(1)).updateRoomStatus(roomId, hotelId, RoomStatus.DIRTY);
        verify(roomService, times(1)).updateRoomStatus(newRoomId, hotelId, RoomStatus.OCCUPIED);
    }

    @Test
    void changeRoomDifferentRoomTypeVoidsAndRepostsSegmentedCharges() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final UUID oldChargeId = Objects.requireNonNull(UUID.randomUUID());
        stay.setRoomChargeId(oldChargeId);
        stay.setRoomChargeUnitPrice(ROOM_CHARGE_UNIT_PRICE);
        stay.setRoomChargeNights(ROOM_CHARGE_ORIGINAL_NIGHTS);
        stay.setActualCheckInTime(LocalDateTime.now().minusDays(CONSUMED_NIGHTS));

        final UUID oldRoomTypeId = Objects.requireNonNull(UUID.randomUUID());
        final UUID newRoomTypeId = Objects.requireNonNull(UUID.randomUUID());
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());
        final RoomResponse oldRoom =
                roomWithType(roomId, ROOM_NUMBER_101, oldRoomTypeId, STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN);
        final RoomResponse newRoom =
                roomWithType(newRoomId, THIRD_ROOM_NUMBER_301, newRoomTypeId, STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(newRoom);
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(oldRoom);
        when(reservationService.isRoomBookedByOthers(newRoomId, LocalDate.now(), stay.getExpectedCheckOutDate()))
                .thenReturn(false);
        when(billingClient.getLatestInvoiceByReservation(reservationId))
                .thenReturn(new InvoiceStatusResponse(UUID.randomUUID(), reservationId, OPEN_STATUS, BigDecimal.ZERO));
        when(billingClient.addCharge(ArgumentMatchers.eq(id), ArgumentMatchers.any()))
                .thenReturn(new ChargeResponse(UUID.randomUUID()));
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(validResponse);

        final StayResponse response = stayService.changeRoom(id, hotelId, newRoomId, null);

        assertNotNull(response);
        assertEquals(newRoomId, stay.getRoomId());
        verify(billingClient, times(1)).removeCharge(id, oldChargeId);
        // Two segments: consumed nights (old room, 2 nights) + remaining nights (new room, 3 nights).
        verify(billingClient, times(2)).addCharge(ArgumentMatchers.eq(id), ArgumentMatchers.any());
        verify(reservationService, times(1))
                .syncLineItemRoomForCheckedInStay(reservationId, roomId, newRoomId, hotelId);
    }

    /**
     * A stay overdue for check-out (nobody extended it, the guest hasn't left)
     * has no valid [today, checkOut) window to search or reprice against —
     * must be rejected explicitly, pointing at Proroga, rather than silently
     * producing an empty room list or a divide-by-negative-nights crash.
     */
    @Test
    void changeRoomRejectsWhenExpectedCheckoutInPast() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setExpectedCheckOutDate(LocalDate.now().minusDays(1));
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, null));
        assertEquals("STAY_ROOM_CHANGE_CHECKOUT_IN_PAST", ex.getMessage());
        verify(roomService, never()).getRoomById(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void changeRoomRejectsSameRoom() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(BadRequestException.class, () -> stayService.changeRoom(id, hotelId, roomId, null));
        verify(roomService, never()).getRoomById(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void changeRoomRejectsWhenTargetNotClean() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.DIRTY));

        final ConflictException ex = assertThrows(ConflictException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, null));
        assertEquals("ROOM_NOT_CLEAN", ex.getMessage());
        verify(reservationService, never())
                .isRoomBookedByOthers(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void changeRoomRejectsWhenCapacityExceeded() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final List<StayGuest> guests = new ArrayList<>();
        guests.add(new StayGuest());
        guests.add(new StayGuest());
        guests.add(new StayGuest());
        stay.setGuests(guests);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN));

        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, null));
        assertEquals("ROOM_CAPACITY_EXCEEDED", ex.getMessage());
    }

    @Test
    void changeRoomExcludesDepartedGuestsFromCapacityCount() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final StayGuest departed = new StayGuest();
        departed.setDepartureDate(LocalDate.now());
        final StayGuest present = new StayGuest();
        final List<StayGuest> guests = new ArrayList<>();
        guests.add(departed);
        guests.add(present);
        stay.setGuests(guests);
        final UUID sharedRoomTypeId = Objects.requireNonNull(UUID.randomUUID());
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        // maxOccupancy 1: passes only if the departed guest is excluded from the count (1 <= 1).
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, sharedRoomTypeId, SINGLE_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(
                roomWithType(roomId, ROOM_NUMBER_101, sharedRoomTypeId, SINGLE_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(reservationService.isRoomBookedByOthers(newRoomId, LocalDate.now(), stay.getExpectedCheckOutDate()))
                .thenReturn(false);
        when(stayRepository.save(stay)).thenReturn(stay);
        when(stayMapper.toDto(stay)).thenReturn(validResponse);

        final StayResponse response = stayService.changeRoom(id, hotelId, newRoomId, null);

        assertNotNull(response);
    }

    @Test
    void changeRoomRejectsWhenRoomBookedByOthers() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(reservationService.isRoomBookedByOthers(newRoomId, LocalDate.now(), stay.getExpectedCheckOutDate()))
                .thenReturn(true);

        final ConflictException ex = assertThrows(ConflictException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, null));
        assertEquals("ROOM_NOT_AVAILABLE_FOR_ROOM_CHANGE", ex.getMessage());
        verify(billingClient, never()).removeCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void changeRoomRejectsWhenInvoiceNotOpenAndRoomTypeDiffers() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setRoomChargeId(Objects.requireNonNull(UUID.randomUUID()));
        stay.setRoomChargeUnitPrice(ROOM_CHARGE_UNIT_PRICE);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(
                roomWithType(roomId, ROOM_NUMBER_101, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(reservationService.isRoomBookedByOthers(newRoomId, LocalDate.now(), stay.getExpectedCheckOutDate()))
                .thenReturn(false);
        when(billingClient.getLatestInvoiceByReservation(reservationId))
                .thenReturn(new InvoiceStatusResponse(UUID.randomUUID(), reservationId, PAID_STATUS, BigDecimal.ZERO));

        final ConflictException ex = assertThrows(ConflictException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, null));
        assertEquals("STAY_ROOM_CHANGE_INVOICE_NOT_OPEN", ex.getMessage());
        verify(billingClient, never()).removeCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void changeRoomRejectsWhenRoomChargeSnapshotMissingAndRoomTypeDiffers() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setRoomChargeId(null);
        stay.setRoomChargeUnitPrice(null);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));
        when(roomService.getRoomById(newRoomId, hotelId)).thenReturn(
                roomWithType(newRoomId, NEW_ROOM_NUMBER_205, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(roomService.getRoomById(roomId, hotelId)).thenReturn(
                roomWithType(roomId, ROOM_NUMBER_101, UUID.randomUUID(), STANDARD_MAX_OCCUPANCY, RoomStatus.CLEAN));
        when(reservationService.isRoomBookedByOthers(newRoomId, LocalDate.now(), stay.getExpectedCheckOutDate()))
                .thenReturn(false);
        when(billingClient.getLatestInvoiceByReservation(reservationId))
                .thenReturn(new InvoiceStatusResponse(UUID.randomUUID(), reservationId, OPEN_STATUS, BigDecimal.ZERO));

        final ConflictException ex = assertThrows(ConflictException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, null));
        assertEquals("ROOM_CHARGE_SNAPSHOT_MISSING", ex.getMessage());
        verify(billingClient, never()).removeCharge(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void changeRoomRejectsWhenStayNotCheckedIn() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setStatus(StayStatus.CHECKED_OUT);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(IllegalStateException.class, () -> stayService.changeRoom(id, hotelId, newRoomId, null));
    }

    @Test
    void changeRoomThrowsNotFoundForUnknownStay() {
        final UUID id = Objects.requireNonNull(stayId);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());
        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> stayService.changeRoom(id, hotelId, newRoomId, null));
    }

    @Test
    void changeRoomRejectsStaleVersion() {
        final UUID id = Objects.requireNonNull(stayId);
        final Stay stay = Objects.requireNonNull(savedStay);
        stay.setVersion(STALE_CURRENT_VERSION);
        final UUID newRoomId = Objects.requireNonNull(UUID.randomUUID());

        when(stayRepository.findByIdAndHotelId(id, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(ConflictException.class,
                () -> stayService.changeRoom(id, hotelId, newRoomId, STALE_CLIENT_VERSION));
        verify(roomService, never()).getRoomById(ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
