package com.hotelpms.inventory.controller;

import com.hotelpms.inventory.domain.RoomStatus;
import com.hotelpms.inventory.dto.RoomRequest;
import com.hotelpms.inventory.dto.RoomResponse;
import com.hotelpms.inventory.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * Creates a room.
     *
     * @param request the request
     * @return the response
     */
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@NonNull @Valid @RequestBody final RoomRequest request) {
        final RoomResponse response = roomService.createRoom(request);
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
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    /**
     * Gets a paginated list of all active rooms.
     * Supports standard Spring Data pagination query parameters:
     * {@code ?page=0&size=20&sort=roomNumber,asc}
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
        return ResponseEntity.ok(roomService.getAllRooms(pageable));
    }

    /**
     * Updates a room.
     *
     * @param id      the id
     * @param request the request
     * @return the response
     */
    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(@NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    /**
     * Updates only the housekeeping status of a room.
     *
     * @param id     the room id
     * @param status the new status
     * @return the updated response
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<RoomResponse> updateRoomStatus(@NonNull @PathVariable final UUID id,
            @NonNull @RequestBody final RoomStatus status) {
        return ResponseEntity.ok(roomService.updateRoomStatus(id, status));
    }

    /**
     * Deletes a room.
     *
     * @param id the id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@NonNull @PathVariable final UUID id) {
        roomService.deleteRoom(id);
    }
}
