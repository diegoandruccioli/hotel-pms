package com.hotelpms.guest.controller;

import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import com.hotelpms.guest.service.GuestService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing guest management endpoints under
 * {@code /api/v1/guests}.
 */
@RestController
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
public final class GuestController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final GuestService guestService;

    /**
     * Creates a new guest profile.
     *
     * @param request the creation request; must be valid and non-null
     * @return {@code 201 Created} with the persisted guest as a response DTO
     */
    @PostMapping
    public ResponseEntity<GuestResponse> createGuest(
            @NonNull @Valid @RequestBody final GuestRequest request) {
        final GuestResponse response = guestService.createGuest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a single guest by their UUID.
     *
     * @param id the guest UUID; must not be {@code null}
     * @return {@code 200 OK} with the guest as a response DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<GuestResponse> getGuestById(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(guestService.getGuestById(id));
    }

    /**
     * Retrieves a paginated list of all registered guests.
     * Supports standard Spring Data pagination query parameters:
     * {@code ?page=0&size=20&sort=lastName,asc}
     *
     * @param pageable the pagination and sorting parameters
     * @return {@code 200 OK} with a page of guest response DTOs
     */
    @GetMapping
    public ResponseEntity<Page<GuestResponse>> getAllGuests(
            @PageableDefault(size = DEFAULT_PAGE_SIZE,
                    sort = "lastName",
                    direction = Sort.Direction.ASC)
            final Pageable pageable) {
        return ResponseEntity.ok(guestService.getAllGuests(pageable));
    }

    /**
     * Updates an existing guest's profile.
     *
     * @param id      the guest UUID; must not be {@code null}
     * @param request the update request; must be valid and non-null
     * @return {@code 200 OK} with the updated guest as a response DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<GuestResponse> updateGuest(
            @NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final GuestRequest request) {
        return ResponseEntity.ok(guestService.updateGuest(id, request));
    }

    /**
     * Soft-deletes a guest profile.
     *
     * @param id the guest UUID; must not be {@code null}
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGuest(@NonNull @PathVariable final UUID id) {
        guestService.deleteGuest(id);
    }

    /**
     * Searches guests by a free-text query across first name, last name, email and
     * city.
     *
     * @param query    the search query; if blank, behaves like getAllGuests
     * @param pageable pagination parameters
     * @return a page of matching guests
     */
    @GetMapping("/search")
    public ResponseEntity<Page<GuestResponse>> searchGuests(
            @RequestParam(name = "query", required = false) final String query,
            @PageableDefault(size = DEFAULT_PAGE_SIZE,
                    sort = "lastName",
                    direction = Sort.Direction.ASC)
            final Pageable pageable) {
        return ResponseEntity.ok(guestService.searchGuests(query, pageable));
    }

    /**
     * Adds an identity document to an existing guest.
     *
     * @param id      the guest UUID; must not be {@code null}
     * @param request the document creation request; must be valid and non-null
     * @return {@code 201 Created} with the persisted document as a response DTO
     */
    @PostMapping("/{id}/documents")
    public ResponseEntity<IdentityDocumentResponseDTO> addIdentityDocument(
            @NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final IdentityDocumentRequestDTO request) {
        final IdentityDocumentResponseDTO response = guestService.addIdentityDocument(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Removes an identity document from a guest.
     *
     * @param id         the guest UUID; must not be {@code null}
     * @param documentId the document UUID to remove; must not be {@code null}
     */
    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeIdentityDocument(
            @NonNull @PathVariable final UUID id,
            @NonNull @PathVariable final UUID documentId) {
        guestService.removeIdentityDocument(id, documentId);
    }

    /**
     * Retrieves a list of guests by their unique IDs.
     *
     * @param ids the list of guest UUIDs
     * @return a list of guest response DTOs
     */
    @PostMapping("/batch")
    public ResponseEntity<List<GuestResponse>> getGuestsBatch(@NonNull @RequestBody final List<UUID> ids) {
        return ResponseEntity.ok(guestService.getGuestsByIds(ids));
    }
}
