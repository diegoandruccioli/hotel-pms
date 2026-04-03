package com.hotelpms.guest.service;

import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface defining the guest management contract.
 */
public interface GuestService {

    /**
     * Creates a new guest profile.
     *
     * @param request the creation request DTO; must not be {@code null}
     * @return the persisted guest as a response DTO
     */
    GuestResponse createGuest(GuestRequest request);

    /**
     * Retrieves a guest by their unique ID.
     *
     * @param id the guest UUID; must not be {@code null}
     * @return the guest as a response DTO
     */
    GuestResponse getGuestById(UUID id);

    /**
     * Retrieves a paginated list of guests registered in the system.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of guest response DTOs
     */
    Page<GuestResponse> getAllGuests(Pageable pageable);

    /**
     * Updates an existing guest's profile.
     *
     * @param id      the guest UUID; must not be {@code null}
     * @param request the update request DTO; must not be {@code null}
     * @return the updated guest as a response DTO
     */
    GuestResponse updateGuest(UUID id, GuestRequest request);

    /**
     * Soft-deletes a guest profile.
     *
     * @param id the guest UUID; must not be {@code null}
     */
    void deleteGuest(UUID id);

    /**
     * Adds an identity document to an existing guest.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param request the document creation request DTO; must not be {@code null}
     * @return the persisted document as a response DTO
     */
    IdentityDocumentResponseDTO addIdentityDocument(UUID guestId, IdentityDocumentRequestDTO request);

    /**
     * Removes an identity document from a guest.
     *
     * @param guestId    the guest UUID; must not be {@code null}
     * @param documentId the document UUID to remove; must not be {@code null}
     */
    void removeIdentityDocument(UUID guestId, UUID documentId);

    /**
     * Performs a text search across first name, last name, email and city.
     *
     * @param query    the search query
     * @param pageable pagination parameters
     * @return a page of matching guest responses
     */
    Page<GuestResponse> searchGuests(String query, Pageable pageable);

    /**
     * Retrieves a list of guests by their unique IDs.
     *
     * @param ids the list of guest UUIDs
     * @return a list of guest response DTOs
     */
    List<GuestResponse> getGuestsByIds(List<UUID> ids);
}
