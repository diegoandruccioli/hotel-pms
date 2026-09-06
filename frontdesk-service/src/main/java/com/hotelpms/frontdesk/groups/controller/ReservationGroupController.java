package com.hotelpms.frontdesk.groups.controller;

import com.hotelpms.frontdesk.groups.dto.GroupCheckoutOutcome;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupCreateRequest;
import com.hotelpms.frontdesk.groups.dto.ReservationGroupResponse;
import com.hotelpms.frontdesk.groups.service.ReservationGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for reservation groups (Punto 4): rooming list, group rate,
 * master folio, and bulk group check-out.
 */
@RestController
@RequestMapping("/api/v1/reservation-groups")
@RequiredArgsConstructor
@Slf4j
public class ReservationGroupController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ReservationGroupService reservationGroupService;

    /**
     * Creates a reservation group together with its rooming list.
     *
     * @param request the group + rooming list to create
     * @return {@code 201 Created} with the created group
     */
    @PostMapping
    public ResponseEntity<ReservationGroupResponse> createGroup(
            @NonNull @Valid @RequestBody final ReservationGroupCreateRequest request) {
        log.info("REST request to create reservation group '{}' with {} rooms",
                request.name(), request.rooms() == null ? 0 : request.rooms().size());
        final ReservationGroupResponse response = reservationGroupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a group by ID, with its rooming list.
     *
     * @param id the group UUID
     * @return the group response
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationGroupResponse> getGroup(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(reservationGroupService.getGroup(id));
    }

    /**
     * Retrieves a paginated list of groups for the authenticated hotel.
     *
     * @param pageable pagination parameters
     * @return a page of group responses
     */
    @GetMapping
    public ResponseEntity<Page<ReservationGroupResponse>> getAllGroups(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "checkInDate",
                    direction = Sort.Direction.DESC) final Pageable pageable) {
        return ResponseEntity.ok(reservationGroupService.getAllGroups(pageable));
    }

    /**
     * Cancels a group and every one of its non-terminal member reservations.
     *
     * @param id      the group UUID
     * @param version the version the caller last read, for optimistic-lock
     *                conflict detection; omitted skips the check
     * @return the cancelled group response
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationGroupResponse> cancelGroup(
            @NonNull @PathVariable final UUID id,
            @RequestParam(required = false) final Long version) {
        log.info("REST request to cancel reservation group {}", id);
        return ResponseEntity.ok(reservationGroupService.cancelGroup(id, version));
    }

    /**
     * Checks out every currently checked-in room in the group.
     *
     * @param id the group UUID
     * @return the per-room checkout outcomes
     */
    @PostMapping("/{id}/checkout")
    public ResponseEntity<List<GroupCheckoutOutcome>> checkoutGroup(@NonNull @PathVariable final UUID id) {
        log.info("REST request to check out reservation group {}", id);
        return ResponseEntity.ok(reservationGroupService.checkoutGroup(id));
    }
}
