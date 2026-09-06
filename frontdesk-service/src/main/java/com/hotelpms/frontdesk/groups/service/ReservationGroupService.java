package com.hotelpms.frontdesk.groups.service;

import com.hotelpms.frontdesk.groups.dto.GroupCheckoutOutcome;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupCreateRequest;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service for reservation groups (Punto 4): rooming list creation, group rate,
 * master folio, and bulk group check-out.
 */
public interface ReservationGroupService {

    /**
     * Creates a reservation group together with its rooming list: one child
     * reservation is created per rooming-list row, all in the same transaction --
     * a failure on any one room (overlap, unavailable room) rolls back the whole
     * group. Optionally opens a MASTER folio in billing-service.
     *
     * @param request the group + rooming list to create
     * @return the created group, with its rooming list
     */
    ReservationGroupResponse createGroup(ReservationGroupCreateRequest request);

    /**
     * Retrieves a group by ID, with its rooming list, scoped to the authenticated hotel.
     *
     * @param id the group UUID
     * @return the group response
     */
    ReservationGroupResponse getGroup(UUID id);

    /**
     * Retrieves a paginated list of groups for the authenticated hotel, most
     * recent check-in date first.
     *
     * @param pageable pagination parameters
     * @return a page of group responses (without their rooming lists, for listing)
     */
    Page<ReservationGroupResponse> getAllGroups(Pageable pageable);

    /**
     * Cancels a group and every one of its non-terminal member reservations.
     *
     * @param id            the group UUID
     * @param clientVersion the version the caller last read, for optimistic-lock
     *                      conflict detection; {@code null} skips the check
     * @return the cancelled group response
     */
    ReservationGroupResponse cancelGroup(UUID id, Long clientVersion);

    /**
     * Checks out every currently checked-in room in the group. One room's
     * checkout guard failing (e.g. unpaid F&amp;B extras) does not block the rest
     * of the group -- every eligible stay is attempted and each result reported.
     *
     * @param id the group UUID
     * @return the per-room checkout outcomes
     */
    List<GroupCheckoutOutcome> checkoutGroup(UUID id);
}
