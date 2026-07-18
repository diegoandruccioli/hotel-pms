package com.hotelpms.frontdesk.rooms.controller;

import com.hotelpms.frontdesk.rooms.dto.RoomTypeRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for Room Types.
 * Write operations (create, update, delete) are restricted to ADMIN and OWNER roles;
 * read operations are available to all authenticated roles including RECEPTIONIST.
 * All operations are scoped to the caller's hotel (T-ROOM-02): a room type belonging
 * to another hotel returns 404, and {@code hotelId} is always resolved server-side,
 * never accepted from the client.
 */
@RestController
@RequestMapping("/api/v1/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    /**
     * Creates a room type, scoped to the caller's hotel. Restricted to ADMIN/OWNER.
     *
     * @param request the request
     * @return the created response
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<RoomTypeResponse> createRoomType(@NonNull @Valid @RequestBody final RoomTypeRequest request) {
        final RoomTypeResponse response = roomTypeService.createRoomType(request, resolveHotelId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a room type by id, scoped to the caller's hotel (T-ROOM-02): a room
     * type belonging to another hotel returns 404.
     *
     * @param id the id
     * @return the response
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> getRoomTypeById(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(roomTypeService.getRoomTypeById(id, resolveHotelId()));
    }

    /**
     * Gets all room types belonging to the caller's hotel (T-ROOM-02).
     *
     * @return the list of responses
     */
    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> getAllRoomTypes() {
        return ResponseEntity.ok(roomTypeService.getAllRoomTypes(resolveHotelId()));
    }

    /**
     * Updates a room type, scoped to the caller's hotel. Restricted to ADMIN/OWNER.
     *
     * @param id      the id
     * @param request the request
     * @return the response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<RoomTypeResponse> updateRoomType(@NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final RoomTypeRequest request) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(id, resolveHotelId(), request));
    }

    /**
     * Deletes a room type, scoped to the caller's hotel. Restricted to ADMIN/OWNER.
     *
     * @param id the id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public void deleteRoomType(@NonNull @PathVariable final UUID id) {
        roomTypeService.deleteRoomType(id, resolveHotelId());
    }

    /**
     * Extracts the hotel UUID from the current security context details.
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
