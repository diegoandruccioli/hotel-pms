package com.hotelpms.inventory.controller;

import com.hotelpms.inventory.dto.RoomTypeRequest;
import com.hotelpms.inventory.dto.RoomTypeResponse;
import com.hotelpms.inventory.service.RoomTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for Room Types.
 */
@RestController
@RequestMapping("/api/v1/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    /**
     * Creates a room type.
     * 
     * @param request the request
     * @return the created response
     */
    @PostMapping
    public ResponseEntity<RoomTypeResponse> createRoomType(@NonNull @Valid @RequestBody final RoomTypeRequest request) {
        final RoomTypeResponse response = roomTypeService.createRoomType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a room type by id.
     * 
     * @param id the id
     * @return the response
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> getRoomTypeById(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(roomTypeService.getRoomTypeById(id));
    }

    /**
     * Gets all room types.
     * 
     * @return the list of responses
     */
    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> getAllRoomTypes() {
        return ResponseEntity.ok(roomTypeService.getAllRoomTypes());
    }

    /**
     * Updates a room type.
     * 
     * @param id      the id
     * @param request the request
     * @return the response
     */
    @PutMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> updateRoomType(@NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final RoomTypeRequest request) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(id, request));
    }

    /**
     * Deletes a room type.
     * 
     * @param id the id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoomType(@NonNull @PathVariable final UUID id) {
        roomTypeService.deleteRoomType(id);
    }
}
