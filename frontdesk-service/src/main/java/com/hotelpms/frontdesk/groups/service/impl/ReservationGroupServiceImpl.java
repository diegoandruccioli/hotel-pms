package com.hotelpms.frontdesk.groups.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.MasterFolioRequest;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.BillingNotPaidException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.groups.domain.GroupStatus;
import com.hotelpms.frontdesk.groups.domain.ReservationGroup;
import com.hotelpms.frontdesk.groups.dto.GroupCheckoutOutcome;
import com.hotelpms.frontdesk.groups.dto.GroupMemberResponse;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupCreateRequest;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupResponse;
import com.hotelpms.frontdesk.groups.dto.RoomingListEntryRequest;
import com.hotelpms.frontdesk.groups.repository.ReservationGroupRepository;
import com.hotelpms.frontdesk.groups.service.ReservationGroupService;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.StayService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ReservationGroupService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationGroupServiceImpl implements ReservationGroupService {

    private static final String GROUP_NOT_FOUND = "GROUP_NOT_FOUND";
    private static final String UNKNOWN_GUEST = "Unknown Guest";
    private static final String HOTEL_ID_NOT_NULL_MSG = "Hotel ID must not be null";
    private static final List<ReservationStatus> TERMINAL_STATUSES = List.of(
            ReservationStatus.CHECKED_OUT, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW);

    private final ReservationGroupRepository groupRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final GuestClient guestClient;
    private final BillingClient billingClient;
    private final StayService stayService;
    private final StayRepository stayRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationGroupResponse createGroup(final ReservationGroupCreateRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new BadRequestException("CHECKOUT_MUST_BE_AFTER_CHECKIN");
        }
        final UUID hotelId = resolveHotelId();
        final GuestResponse contactGuest = verifyGuestExists(request.contactGuestId());

        final ReservationGroup group = ReservationGroup.builder()
                .hotelId(hotelId)
                .name(request.name())
                .companyName(request.companyName())
                .contactGuestId(request.contactGuestId())
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .status(GroupStatus.CONFIRMED)
                .groupRatePerNight(request.groupRatePerNight())
                .notes(request.notes())
                .build();
        final ReservationGroup savedGroup = groupRepository.save(group);

        if (request.openMasterFolio()) {
            final InvoiceCreatedResponse masterFolio = billingClient.createMasterFolioForGroup(
                    savedGroup.getId(), new MasterFolioRequest(request.contactGuestId()));
            if (masterFolio == null || masterFolio.id() == null) {
                throw new ExternalServiceException("MASTER_FOLIO_CREATION_FAILED");
            }
            savedGroup.setMasterFolioInvoiceId(masterFolio.id());
            groupRepository.save(savedGroup);
        }

        final List<GroupMemberResponse> members = new ArrayList<>();
        for (final RoomingListEntryRequest room : request.rooms()) {
            final ReservationResponse created = reservationService.createReservationForGroup(
                    savedGroup.getId(), room.guestId(), room.roomId(), room.expectedGuests(),
                    request.checkInDate(), request.checkOutDate(), request.groupRatePerNight(),
                    room.billedToMasterFolio() && request.openMasterFolio());
            members.add(toMemberResponse(created, room.billedToMasterFolio() && request.openMasterFolio()));
        }

        log.info("[GROUP] CREATED | groupId={} | hotelId={} | rooms={}",
                savedGroup.getId(), hotelId, members.size());
        return toResponse(savedGroup, contactGuest.firstName() + " " + contactGuest.lastName(), members);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ReservationGroupResponse getGroup(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final ReservationGroup group = findGroupOrThrow(id, hotelId);
        return enrich(group);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<ReservationGroupResponse> getAllGroups(final Pageable pageable) {
        final UUID hotelId = resolveHotelId();
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        return groupRepository.findAllByHotelId(hotelId, safePageable).map(this::enrich);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationGroupResponse cancelGroup(final UUID id, final Long clientVersion) {
        final UUID hotelId = resolveHotelId();
        final ReservationGroup group = findGroupOrThrow(id, hotelId);
        if (clientVersion != null && !clientVersion.equals(group.getVersion())) {
            throw new ConflictException("GROUP_STALE_VERSION");
        }

        final List<Reservation> members = reservationRepository.findAllByGroupIdAndHotelId(id, hotelId);
        for (final Reservation member : members) {
            if (!TERMINAL_STATUSES.contains(member.getStatus())) {
                reservationService.updateStatusAndGuestsForHotel(
                        hotelId, member.getId(), ReservationStatus.CANCELLED, null, null);
            }
        }

        group.setStatus(GroupStatus.CANCELLED);
        final ReservationGroup saved = groupRepository.save(group);
        log.info("[GROUP] CANCELLED | groupId={} | hotelId={} | membersCancelled={}", id, hotelId, members.size());
        return enrich(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately NOT {@code @Transactional} at this method's level: each
     * member's {@code stayService.checkOut} call must commit or fail
     * independently. Wrapping the whole loop in one transaction would let a
     * later room's failure mark the shared transaction rollback-only (Spring's
     * standard behavior when an exception propagates out of a nested {@code
     * @Transactional} proxy boundary, even if the caller then catches it) and
     * silently undo every room that had already checked out successfully.
     */
    @Override
    public List<GroupCheckoutOutcome> checkoutGroup(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final ReservationGroup group = findGroupOrThrow(id, hotelId);
        final List<Reservation> members = reservationRepository.findAllByGroupIdAndHotelId(id, hotelId);

        final List<GroupCheckoutOutcome> outcomes = new ArrayList<>();
        boolean allCheckedOut = !members.isEmpty();
        for (final Reservation member : members) {
            final Optional<Stay> activeStay = stayRepository.findAllByReservationId(member.getId()).stream()
                    .filter(s -> s.getStatus() == StayStatus.CHECKED_IN)
                    .findFirst();
            if (activeStay.isEmpty()) {
                allCheckedOut = allCheckedOut && member.getStatus() == ReservationStatus.CHECKED_OUT;
                continue;
            }
            final UUID stayId = activeStay.get().getId();
            try {
                stayService.checkOut(stayId, hotelId);
                outcomes.add(new GroupCheckoutOutcome(member.getId(), stayId, true, null));
            } catch (final BillingNotPaidException | ExternalServiceException
                    | NotFoundException | IllegalStateException ex) {
                allCheckedOut = false;
                log.warn("[GROUP] CHECKOUT_MEMBER_FAILED | groupId={} | reservationId={} | stayId={} | reason={}",
                        id, member.getId(), stayId, ex.getMessage());
                outcomes.add(new GroupCheckoutOutcome(member.getId(), stayId, false, ex.getMessage()));
            }
        }

        if (allCheckedOut) {
            group.setStatus(GroupStatus.CHECKED_OUT);
            groupRepository.save(group);
        }
        return List.copyOf(outcomes);
    }

    private ReservationGroupResponse enrich(final ReservationGroup group) {
        final List<Reservation> memberReservations =
                reservationRepository.findAllByGroupIdAndHotelId(group.getId(), group.getHotelId());

        final List<UUID> guestIds = new ArrayList<>(memberReservations.stream()
                .map(Reservation::getGuestId)
                .distinct()
                .toList());
        if (!guestIds.contains(group.getContactGuestId())) {
            guestIds.add(group.getContactGuestId());
        }
        final Map<UUID, String> guestNames = guestClient.getGuestsBatch(guestIds).stream()
                .collect(Collectors.toMap(GuestResponse::id, g -> g.firstName() + " " + g.lastName()));

        final List<GroupMemberResponse> members = memberReservations.stream()
                .map(r -> new GroupMemberResponse(
                        r.getId(), r.getGuestId(), guestNames.getOrDefault(r.getGuestId(), UNKNOWN_GUEST),
                        r.getLineItems().isEmpty() ? null : r.getLineItems().get(0).getRoomId(),
                        r.getExpectedGuests(), r.getActualGuests(), r.getCheckInDate(), r.getCheckOutDate(),
                        r.getStatus(), r.isBilledToMasterFolio(),
                        r.getLineItems().isEmpty() ? null : r.getLineItems().get(0).getPrice()))
                .toList();

        return toResponse(group, guestNames.getOrDefault(group.getContactGuestId(), UNKNOWN_GUEST), members);
    }

    private GroupMemberResponse toMemberResponse(final ReservationResponse created, final boolean billedToMasterFolio) {
        return new GroupMemberResponse(
                created.id(), created.guestId(), created.guestFullName(),
                created.lineItems() == null || created.lineItems().isEmpty()
                        ? null : created.lineItems().get(0).roomId(),
                created.expectedGuests(), created.actualGuests(), created.checkInDate(), created.checkOutDate(),
                created.status(), billedToMasterFolio,
                created.lineItems() == null || created.lineItems().isEmpty()
                        ? null : created.lineItems().get(0).price());
    }

    private ReservationGroupResponse toResponse(
            final ReservationGroup group, final String contactGuestName, final List<GroupMemberResponse> members) {
        return new ReservationGroupResponse(
                group.getId(), group.getName(), group.getCompanyName(), group.getContactGuestId(), contactGuestName,
                group.getCheckInDate(), group.getCheckOutDate(), group.getStatus(), group.getGroupRatePerNight(),
                group.getMasterFolioInvoiceId(), group.getNotes(), members, group.isActive(),
                group.getCreatedAt(), group.getUpdatedAt(), group.getVersion());
    }

    private ReservationGroup findGroupOrThrow(final UUID id, final UUID hotelId) {
        return groupRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
    }

    private GuestResponse verifyGuestExists(final UUID guestId) {
        if (guestId == null) {
            throw new IllegalArgumentException("Guest ID cannot be null");
        }
        try {
            return guestClient.getGuestById(guestId);
        } catch (final FeignException.NotFound e) {
            throw new BadRequestException("GUEST_NOT_FOUND", e);
        } catch (final FeignException e) {
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE", e);
        }
    }

    @NonNull
    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr)) {
            throw new IllegalStateException(HOTEL_ID_NOT_NULL_MSG);
        }
        return UUID.fromString(hotelIdStr);
    }
}
