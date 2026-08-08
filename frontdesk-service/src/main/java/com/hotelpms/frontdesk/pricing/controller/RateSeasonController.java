package com.hotelpms.frontdesk.pricing.controller;

import com.hotelpms.frontdesk.pricing.dto.RateSeasonRequest;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.service.RateSeasonAdminService;
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
 * Controller for seasonal rates (rate seasons), nested under a room type.
 * Write operations are restricted to ADMIN and OWNER (same policy as
 * {@code RoomTypeController}, and already enforced a second time at the
 * gateway via {@code WRITE_RESTRICTED_PREFIXES} on {@code /api/v1/room-types}).
 * All operations are scoped to the caller's hotel — a season or room type
 * belonging to another hotel is never visible (T-ROOM-02).
 */
@RestController
@RequestMapping("/api/v1/room-types/{roomTypeId}/rate-seasons")
@RequiredArgsConstructor
public class RateSeasonController {

    private final RateSeasonAdminService rateSeasonAdminService;

    /**
     * Lists every active season for a room type, oldest first.
     *
     * @param roomTypeId the room type UUID
     * @return the seasons
     */
    @GetMapping
    public ResponseEntity<List<RateSeasonResponse>> listSeasons(@NonNull @PathVariable final UUID roomTypeId) {
        return ResponseEntity.ok(rateSeasonAdminService.listSeasons(roomTypeId, resolveHotelId()));
    }

    /**
     * Creates a season for a room type. Restricted to ADMIN/OWNER.
     *
     * @param roomTypeId the room type UUID
     * @param request    the season details
     * @return the created season
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<RateSeasonResponse> createSeason(
            @NonNull @PathVariable final UUID roomTypeId,
            @NonNull @Valid @RequestBody final RateSeasonRequest request) {
        final RateSeasonResponse response =
                rateSeasonAdminService.createSeason(roomTypeId, resolveHotelId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates a season. Restricted to ADMIN/OWNER.
     *
     * @param roomTypeId the room type UUID (path consistency; ownership is
     *                   resolved from the season id itself)
     * @param id         the season UUID
     * @param request    the updated season details
     * @return the updated season
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<RateSeasonResponse> updateSeason(
            @NonNull @PathVariable final UUID roomTypeId,
            @NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final RateSeasonRequest request) {
        return ResponseEntity.ok(rateSeasonAdminService.updateSeason(id, resolveHotelId(), request));
    }

    /**
     * Soft-deletes a season. Restricted to ADMIN/OWNER.
     *
     * @param roomTypeId the room type UUID (path consistency)
     * @param id         the season UUID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public void deleteSeason(@NonNull @PathVariable final UUID roomTypeId, @NonNull @PathVariable final UUID id) {
        rateSeasonAdminService.deleteSeason(id, resolveHotelId());
    }

    /**
     * Extracts the hotel UUID from the current security context details.
     *
     * @return the hotel UUID of the authenticated user
     */
    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }
}
