package com.hotelpms.frontdesk.groups.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.MasterFolioRequest;
import com.hotelpms.frontdesk.exception.BillingNotPaidException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.groups.domain.GroupStatus;
import com.hotelpms.frontdesk.groups.domain.ReservationGroup;
import com.hotelpms.frontdesk.groups.dto.GroupCheckoutOutcome;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupCreateRequest;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupResponse;
import com.hotelpms.frontdesk.groups.dto.RoomingListEntryRequest;
import com.hotelpms.frontdesk.groups.repository.ReservationGroupRepository;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.StayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationGroupServiceImplTest {

    private static final String FIRST_NAME = "Mario";
    private static final String LAST_NAME = "Rossi";
    private static final String EMAIL = "mario@test.com";
    private static final int EXPECTED_GUESTS = 2;

    @Mock
    private ReservationGroupRepository groupRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationService reservationService;

    @Mock
    private GuestClient guestClient;

    @Mock
    private BillingClient billingClient;

    @Mock
    private StayService stayService;

    @Mock
    private StayRepository stayRepository;

    @InjectMocks
    private ReservationGroupServiceImpl reservationGroupService;

    private UUID hotelId;
    private UUID contactGuestId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        contactGuestId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("staff", "", List.of());
        auth.setDetails(hotelId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ReservationGroup group(final GroupStatus status) {
        return ReservationGroup.builder()
                .id(groupId)
                .version(0L)
                .hotelId(hotelId)
                .name("Acme Corp Offsite")
                .contactGuestId(contactGuestId)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .status(status)
                .build();
    }

    private Reservation member(final UUID reservationId, final ReservationStatus status) {
        final Reservation reservation = Reservation.builder()
                .id(reservationId)
                .hotelId(hotelId)
                .guestId(UUID.randomUUID())
                .expectedGuests(EXPECTED_GUESTS)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .status(status)
                .groupId(groupId)
                .build();
        reservation.setLineItems(List.of(ReservationLineItem.builder()
                .roomId(UUID.randomUUID())
                .price(BigDecimal.valueOf(200))
                .build()));
        return reservation;
    }

    @Test
    void shouldCreateGroupWithoutMasterFolioAndOneRoom() {
        final UUID roomId = UUID.randomUUID();
        final UUID guestId = UUID.randomUUID();
        final ReservationGroupCreateRequest request = new ReservationGroupCreateRequest(
                "Acme Corp Offsite", "Acme Corp", contactGuestId,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                null, null, false,
                List.of(new RoomingListEntryRequest(guestId, roomId, EXPECTED_GUESTS, false)));

        when(guestClient.getGuestById(contactGuestId))
                .thenReturn(new GuestResponse(contactGuestId, FIRST_NAME, LAST_NAME, EMAIL));
        when(groupRepository.save(ArgumentMatchers.any(ReservationGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        final ReservationResponse createdMember = new ReservationResponse(
                UUID.randomUUID(), guestId, "Jane Doe", EXPECTED_GUESTS, 0,
                request.checkInDate(), request.checkOutDate(), ReservationStatus.CONFIRMED,
                List.of(), true, null, null, false, null, null);
        when(reservationService.createReservationForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.eq(guestId), ArgumentMatchers.eq(roomId),
                ArgumentMatchers.eq(EXPECTED_GUESTS), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.isNull(), ArgumentMatchers.eq(false)))
                .thenReturn(createdMember);

        final ReservationGroupResponse response = reservationGroupService.createGroup(request);

        assertNotNull(response);
        verify(billingClient, never()).createMasterFolioForGroup(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void shouldOpenMasterFolioWhenRequested() {
        final UUID roomId = UUID.randomUUID();
        final UUID guestId = UUID.randomUUID();
        final ReservationGroupCreateRequest request = new ReservationGroupCreateRequest(
                "Acme Corp Offsite", null, contactGuestId,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                BigDecimal.valueOf(100), null, true,
                List.of(new RoomingListEntryRequest(guestId, roomId, EXPECTED_GUESTS, true)));

        when(guestClient.getGuestById(contactGuestId))
                .thenReturn(new GuestResponse(contactGuestId, FIRST_NAME, LAST_NAME, EMAIL));
        when(groupRepository.save(ArgumentMatchers.any(ReservationGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(billingClient.createMasterFolioForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.any(MasterFolioRequest.class)))
                .thenReturn(new InvoiceCreatedResponse(UUID.randomUUID()));
        final ReservationResponse createdMember = new ReservationResponse(
                UUID.randomUUID(), guestId, "Jane Doe", EXPECTED_GUESTS, 0,
                request.checkInDate(), request.checkOutDate(), ReservationStatus.CONFIRMED,
                List.of(), true, null, null, false, null, null);
        when(reservationService.createReservationForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.eq(guestId), ArgumentMatchers.eq(roomId),
                ArgumentMatchers.eq(EXPECTED_GUESTS), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.eq(BigDecimal.valueOf(100)), ArgumentMatchers.eq(true)))
                .thenReturn(createdMember);

        reservationGroupService.createGroup(request);

        verify(billingClient).createMasterFolioForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.any(MasterFolioRequest.class));
        verify(reservationService).createReservationForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.eq(guestId), ArgumentMatchers.eq(roomId),
                ArgumentMatchers.eq(EXPECTED_GUESTS), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.eq(BigDecimal.valueOf(100)), ArgumentMatchers.eq(true));
    }

    @Test
    void shouldThrowWhenMasterFolioCreationFails() {
        final UUID roomId = UUID.randomUUID();
        final UUID guestId = UUID.randomUUID();
        final ReservationGroupCreateRequest request = new ReservationGroupCreateRequest(
                "Acme Corp Offsite", null, contactGuestId,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                null, null, true,
                List.of(new RoomingListEntryRequest(guestId, roomId, EXPECTED_GUESTS, true)));

        when(guestClient.getGuestById(contactGuestId))
                .thenReturn(new GuestResponse(contactGuestId, FIRST_NAME, LAST_NAME, EMAIL));
        when(groupRepository.save(ArgumentMatchers.any(ReservationGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(billingClient.createMasterFolioForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.any(MasterFolioRequest.class)))
                .thenReturn(null);

        assertThrows(ExternalServiceException.class, () -> reservationGroupService.createGroup(request));
        verify(reservationService, never()).createReservationForGroup(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldGetGroupWithEnrichedMembersAndContactGuestName() {
        final UUID reservationId = UUID.randomUUID();
        final Reservation memberReservation = member(reservationId, ReservationStatus.CONFIRMED);

        when(groupRepository.findByIdAndHotelId(groupId, hotelId))
                .thenReturn(java.util.Optional.of(group(GroupStatus.CONFIRMED)));
        when(reservationRepository.findAllByGroupIdAndHotelId(groupId, hotelId))
                .thenReturn(List.of(memberReservation));
        when(guestClient.getGuestsBatch(ArgumentMatchers.any())).thenReturn(List.of(
                new GuestResponse(memberReservation.getGuestId(), "Jane", "Doe", "jane@test.com"),
                new GuestResponse(contactGuestId, FIRST_NAME, LAST_NAME, EMAIL)));

        final ReservationGroupResponse response = reservationGroupService.getGroup(groupId);

        assertEquals("Mario Rossi", response.contactGuestName());
        assertEquals(1, response.members().size());
        assertEquals("Jane Doe", response.members().get(0).guestFullName());
    }

    @Test
    void shouldThrowNotFoundWhenGroupDoesNotBelongToHotel() {
        when(groupRepository.findByIdAndHotelId(groupId, hotelId)).thenReturn(java.util.Optional.empty());

        assertThrows(NotFoundException.class, () -> reservationGroupService.getGroup(groupId));
    }

    @Test
    void shouldCancelGroupAndItsNonTerminalMembers() {
        final UUID confirmedReservationId = UUID.randomUUID();
        final UUID cancelledReservationId = UUID.randomUUID();
        final ReservationGroup existingGroup = group(GroupStatus.CONFIRMED);

        when(groupRepository.findByIdAndHotelId(groupId, hotelId)).thenReturn(java.util.Optional.of(existingGroup));
        when(reservationRepository.findAllByGroupIdAndHotelId(groupId, hotelId)).thenReturn(List.of(
                member(confirmedReservationId, ReservationStatus.CONFIRMED),
                member(cancelledReservationId, ReservationStatus.CANCELLED)));
        when(groupRepository.save(ArgumentMatchers.any(ReservationGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(guestClient.getGuestsBatch(ArgumentMatchers.any())).thenReturn(List.of());

        final ReservationGroupResponse response = reservationGroupService.cancelGroup(groupId, 0L);

        assertEquals(GroupStatus.CANCELLED, response.status());
        verify(reservationService).updateStatusAndGuestsForHotel(
                hotelId, confirmedReservationId, ReservationStatus.CANCELLED, null, null);
        verify(reservationService, never()).updateStatusAndGuestsForHotel(
                Objects.requireNonNull(hotelId), Objects.requireNonNull(cancelledReservationId),
                ReservationStatus.CANCELLED, null, null);
    }

    @Test
    void shouldRejectCancelGroupWithStaleVersion() {
        when(groupRepository.findByIdAndHotelId(groupId, hotelId))
                .thenReturn(java.util.Optional.of(group(GroupStatus.CONFIRMED)));

        assertThrows(
                com.hotelpms.frontdesk.exception.ConflictException.class,
                () -> reservationGroupService.cancelGroup(groupId, 99L));
        verify(groupRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void shouldCheckOutEveryCheckedInMemberAndMarkGroupCheckedOutOnFullSuccess() {
        final UUID reservationId = UUID.randomUUID();
        final Reservation checkedInMember = member(reservationId, ReservationStatus.CHECKED_IN);
        final UUID stayId = UUID.randomUUID();
        final Stay checkedInStay = Stay.builder().id(stayId).status(StayStatus.CHECKED_IN).build();

        when(groupRepository.findByIdAndHotelId(groupId, hotelId))
                .thenReturn(java.util.Optional.of(group(GroupStatus.CONFIRMED)));
        when(reservationRepository.findAllByGroupIdAndHotelId(groupId, hotelId))
                .thenReturn(List.of(checkedInMember));
        when(stayRepository.findAllByReservationId(reservationId)).thenReturn(List.of(checkedInStay));
        when(groupRepository.save(ArgumentMatchers.any(ReservationGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        final List<GroupCheckoutOutcome> outcomes = reservationGroupService.checkoutGroup(groupId);

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0).success());
        verify(stayService).checkOut(stayId, hotelId);
        verify(groupRepository).save(ArgumentMatchers.argThat(g -> g.getStatus() == GroupStatus.CHECKED_OUT));
    }

    @Test
    void shouldReportPerRoomFailureAndNotMarkGroupCheckedOutOnPartialFailure() {
        final UUID reservationId = UUID.randomUUID();
        final Reservation checkedInMember = member(reservationId, ReservationStatus.CHECKED_IN);
        final UUID stayId = UUID.randomUUID();
        final Stay checkedInStay = Stay.builder().id(stayId).status(StayStatus.CHECKED_IN).build();

        when(groupRepository.findByIdAndHotelId(groupId, hotelId))
                .thenReturn(java.util.Optional.of(group(GroupStatus.CONFIRMED)));
        when(reservationRepository.findAllByGroupIdAndHotelId(groupId, hotelId))
                .thenReturn(List.of(checkedInMember));
        when(stayRepository.findAllByReservationId(reservationId)).thenReturn(List.of(checkedInStay));
        org.mockito.Mockito.doThrow(new BillingNotPaidException("BILLING_NOT_PAID"))
                .when(stayService).checkOut(stayId, hotelId);

        final List<GroupCheckoutOutcome> outcomes = reservationGroupService.checkoutGroup(groupId);

        assertEquals(1, outcomes.size());
        assertFalse(outcomes.get(0).success());
        assertEquals("BILLING_NOT_PAID", outcomes.get(0).errorCode());
        verify(groupRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void shouldSkipMembersWithNoActiveStayDuringCheckout() {
        final UUID reservationId = UUID.randomUUID();
        final Reservation notCheckedIn = member(reservationId, ReservationStatus.CONFIRMED);

        when(groupRepository.findByIdAndHotelId(groupId, hotelId))
                .thenReturn(java.util.Optional.of(group(GroupStatus.CONFIRMED)));
        when(reservationRepository.findAllByGroupIdAndHotelId(groupId, hotelId))
                .thenReturn(List.of(notCheckedIn));
        when(stayRepository.findAllByReservationId(reservationId)).thenReturn(List.of());

        final List<GroupCheckoutOutcome> outcomes = reservationGroupService.checkoutGroup(groupId);

        assertEquals(0, outcomes.size());
        verify(stayService, never()).checkOut(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(groupRepository, never()).save(ArgumentMatchers.any());
    }
}
