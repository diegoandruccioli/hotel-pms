package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.ReservationClient;
import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import com.hotelpms.guest.exception.NotFoundException;
import com.hotelpms.guest.mapper.GuestMapper;
import com.hotelpms.guest.mapper.IdentityDocumentMapper;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.model.IdentityDocument;
import com.hotelpms.guest.repository.GuestRepository;
import com.hotelpms.guest.repository.IdentityDocumentRepository;
import com.hotelpms.guest.service.GuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link GuestService}.
 *
 * <p>
 * {@code GuestService} defines the extension point via its interface contract;
 * this implementation is a closed leaf. The class is non-{@code final} so that
 * Spring's CGLIB proxy can subclass it for {@code @Transactional} AOP advice.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class GuestServiceImpl implements GuestService {

    private static final String GUEST_NOT_FOUND_MSG = "GUEST_NOT_FOUND";

    private final GuestRepository guestRepository;
    private final IdentityDocumentRepository identityDocumentRepository;
    private final GuestMapper guestMapper;
    private final IdentityDocumentMapper identityDocumentMapper;
    private final ReservationClient reservationClient;

    /**
     * Creates and persists a new guest profile.
     *
     * @param request the creation request; must not be {@code null}
     * @return the persisted guest as a response DTO
     */
    @Override
    @Transactional
    public GuestResponse createGuest(final GuestRequest request) {
        final Guest entity = guestMapper.toEntity(request);
        entity.setActive(true);
        final Guest saved = guestRepository.save(entity);
        return guestMapper.toResponse(saved);
    }

    /**
     * Retrieves an active guest by UUID.
     *
     * @param id the guest UUID; must not be {@code null}
     * @return the guest as a response DTO
     * @throws NotFoundException if no guest with the given ID exists
     */
    @Override
    @Transactional(readOnly = true)
    public GuestResponse getGuestById(final UUID id) {
        return guestMapper.toResponse(resolveGuest(id));
    }

    /**
     * Retrieves a paginated list of guests in the system.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of guest response DTOs
     */
    @Override
    @Transactional(readOnly = true)
    public Page<GuestResponse> getAllGuests(final Pageable pageable) {
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        return guestRepository.findAll(safePageable).map(guestMapper::toResponse);
    }

    /**
     * Updates an existing guest's profile fields in-place.
     *
     * @param id      the guest UUID; must not be {@code null}
     * @param request the update request; must not be {@code null}
     * @return the updated guest as a response DTO
     * @throws NotFoundException if no guest with the given ID exists
     */
    @Override
    @Transactional
    public GuestResponse updateGuest(final UUID id, final GuestRequest request) {
        final Guest guest = Objects.requireNonNull(resolveGuest(id));
        guestMapper.updateEntityFromRequest(request, guest);
        final Guest savedGuest = Objects.requireNonNull(guestRepository.save(guest));
        return guestMapper.toResponse(savedGuest);
    }

    /**
     * Soft-deletes a guest profile after verifying there are no active
     * reservations.
     *
     * @param id the guest UUID; must not be {@code null}
     * @throws IllegalStateException if the guest has active reservations
     * @throws NotFoundException     if no guest with the given ID exists
     */
    @Override
    @Transactional
    public void deleteGuest(final UUID id) {
        final Guest guest = resolveGuest(id);
        if (reservationClient.hasActiveReservations(id)) {
            throw new IllegalStateException("GUEST_HAS_ACTIVE_RESERVATIONS");
        }
        guestRepository.delete(Objects.requireNonNull(guest));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<GuestResponse> searchGuests(final String query, final Pageable pageable) {
        final String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty()) {
            return getAllGuests(pageable);
        }
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        return guestRepository.searchByKeyword(safeQuery, safePageable)
                .map(guestMapper::toResponse);
    }

    /**
     * Adds a new identity document to an existing guest.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param request the document creation request; must not be {@code null}
     * @return the persisted document as a response DTO
     * @throws NotFoundException if no guest with the given ID exists
     */
    @Override
    @Transactional
    public IdentityDocumentResponseDTO addIdentityDocument(final UUID guestId,
            final IdentityDocumentRequestDTO request) {
        final Guest guest = resolveGuest(guestId);
        final IdentityDocument document = identityDocumentMapper.toEntity(request);
        document.setGuest(guest);
        document.setActive(true);

        guest.getIdentityDocuments().add(document);
        final IdentityDocument saved = identityDocumentRepository.save(document);
        guestRepository.save(guest);

        return identityDocumentMapper.toResponse(saved);
    }

    /**
     * Removes an identity document from a guest, verifying ownership before
     * deletion.
     *
     * @param guestId    the guest UUID; must not be {@code null}
     * @param documentId the document UUID to remove; must not be {@code null}
     * @throws IllegalArgumentException if the document does not belong to the
     *                                  specified guest
     * @throws NotFoundException        if the guest or document is not found
     */
    @Override
    @Transactional
    public void removeIdentityDocument(final UUID guestId, final UUID documentId) {
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        final Guest guest = resolveGuest(guestId);
        final IdentityDocument document = identityDocumentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND"));

        if (!document.getGuest().getId().equals(guestId)) {
            throw new IllegalArgumentException("DOCUMENT_MISMATCH");
        }

        guest.getIdentityDocuments().remove(document);
        identityDocumentRepository.delete(document);
        guestRepository.save(guest);
    }

    /**
     * Resolves a {@link Guest} entity by ID or throws {@link NotFoundException}.
     * Centralises the null-check and repository lookup used by every public method.
     *
     * @param id the guest UUID; must not be {@code null}
     * @return the resolved guest entity
     * @throws NotFoundException if no guest with the given ID exists
     */
    private Guest resolveGuest(final UUID id) {
        Objects.requireNonNull(id, "Guest ID cannot be null");
        return guestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(GUEST_NOT_FOUND_MSG));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<GuestResponse> getGuestsByIds(final List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return guestRepository.findAllById(ids).stream()
                .map(guestMapper::toResponse)
                .toList();
    }
}
