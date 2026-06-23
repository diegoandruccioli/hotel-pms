package com.hotelpms.frontdesk.rooms.controller;

import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.dto.RoomRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.dto.RoomStatusRequest;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller for Rooms.
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final RoomService roomService;
    private final ReservationService reservationService;

    /**
     * Creates a room, scoped to the caller's hotel (T-ROOM-01): any
     * {@code hotelId} present in the request body is overridden.
     *
     * @param request the request
     * @return the response
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<RoomResponse> createRoom(@NonNull @Valid @RequestBody final RoomRequest request) {
        final RoomResponse response = roomService.createRoom(request, resolveHotelId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a room by id.
     *
     * @param id the id
     * @return the response
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(roomService.getRoomById(id, resolveHotelId()));
    }

    /**
     * Gets a paginated list of all active rooms belonging to the caller's
     * hotel (T-ROOM-01). Supports standard Spring Data pagination query
     * parameters: {@code ?page=0&size=20&sort=roomNumber,asc}
     * Defaults to page 0, 20 items per page, sorted by roomNumber ascending.
     *
     * @param pageable the pagination and sorting parameters, resolved from request
     *                 query params
     * @return a page of room responses
     */
    @GetMapping
    public ResponseEntity<Page<RoomResponse>> getAllRooms(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "roomNumber",
                    direction = Sort.Direction.ASC) final Pageable pageable) {
        return ResponseEntity.ok(roomService.getAllRooms(pageable, resolveHotelId()));
    }

    /**
     * Lists the rooms available for a given date range: housekeeping-{@code
     * CLEAN} and free of any overlapping reservation, scoped to the caller's
     * hotel. {@code checkOutDate} follows the same exclusive-day convention
     * as reservation booking.
     *
     * @param checkInDate  the check-in date (inclusive)
     * @param checkOutDate the check-out date (exclusive); must be after {@code checkInDate}
     * @return the available rooms for that range
     */
    @GetMapping("/availability")
    public ResponseEntity<List<RoomResponse>> getAvailableRooms(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate checkInDate,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate checkOutDate) {
        return ResponseEntity.ok(reservationService.getAvailableRooms(checkInDate, checkOutDate));
    }

    /**
     * Updates a room.
     *
     * @param id      the id
     * @param request the request
     * @return the response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<RoomResponse> updateRoom(@NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, resolveHotelId(), request));
    }

    /**
     * Updates only the housekeeping status of a room.
     *
     * @param id      the room id
     * @param request the status update request
     * @return the updated response
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'RECEPTIONIST')")
    public ResponseEntity<RoomResponse> updateRoomStatus(@NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final RoomStatusRequest request) {
        return ResponseEntity.ok(roomService.updateRoomStatus(id, resolveHotelId(), request.status()));
    }

    /**
     * Deletes a room.
     *
     * @param id the id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public void deleteRoom(@NonNull @PathVariable final UUID id) {
        roomService.deleteRoom(id, resolveHotelId());
    }

    /**
     * Extracts the hotel UUID from the authenticated user's security context.
     * The value is set by the internal auth filter from the {@code X-Auth-Hotel}
     * header injected by the API Gateway.
     *
     * @return the hotel UUID of the authenticated user
     */
    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }
}
