package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.BillingServiceClient;
import com.hotelpms.guest.client.ReservationClient;
import com.hotelpms.guest.client.StayServiceClient;
import com.hotelpms.guest.client.dto.GuestInvoiceClientResponse;
import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import com.hotelpms.guest.exception.GdprLegalHoldException;
import com.hotelpms.guest.exception.GdprLegalHoldException.LegalBasis;
import com.hotelpms.guest.exception.NotFoundException;
import com.hotelpms.guest.mapper.GuestMapper;
import com.hotelpms.guest.mapper.IdentityDocumentMapper;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.model.IdentityDocument;
import com.hotelpms.guest.repository.GuestRepository;
import com.hotelpms.guest.repository.IdentityDocumentRepository;
import com.hotelpms.guest.service.GuestPrivacySettingsService;
import com.hotelpms.guest.service.GuestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link GuestService}.
 *
 * <p>Every data access operation is scoped to the caller's hotel UUID, extracted
 * from the {@link Authentication#getDetails()} value set by
 * {@code InternalAuthFilter} (originating from the {@code X-Auth-Hotel} header
 * injected by the API Gateway). This prevents both IDOR (T-GST-01) and
 * cross-hotel data leaks (T-GST-03).
 *
 * <p>The class is non-{@code final} so that Spring CGLIB can subclass it for
 * {@code @Transactional} AOP advice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("checkstyle:DesignForExtension")
public class GuestServiceImpl implements GuestService {

    private static final String GUEST_NOT_FOUND_MSG = "GUEST_NOT_FOUND";
    private static final String ANON_FIRST = "GDPR";
    private static final String ANON_LAST_PREFIX = "ERASED_";

    private final GuestRepository guestRepository;
    private final IdentityDocumentRepository identityDocumentRepository;
    private final GuestMapper guestMapper;
    private final IdentityDocumentMapper identityDocumentMapper;
    private final ReservationClient reservationClient;
    private final StayServiceClient stayServiceClient;
    private final BillingServiceClient billingServiceClient;
    private final GuestPrivacySettingsService privacySettingsService;

    /**
     * Creates and persists a new guest profile, stamping the caller's hotel ID.
     *
     * @param request the creation request; must not be {@code null}
     * @return the persisted guest as a response DTO
     */
    @Override
    @Transactional
    public GuestResponse createGuest(final GuestRequest request) {
        final UUID hotelId = extractHotelId();
        final Guest entity = guestMapper.toEntity(request);
        entity.setActive(true);
        entity.setHotelId(hotelId);
        entity.setGdprConsentDate(LocalDate.now());
        final Guest saved = guestRepository.save(entity);
        return guestMapper.toResponse(saved);
    }

    /**
     * Retrieves an active guest by UUID, verifying hotel ownership.
     *
     * @param id the guest UUID; must not be {@code null}
     * @return the guest as a response DTO
     * @throws NotFoundException if no guest with the given ID exists in this hotel
     */
    @Override
    @Transactional(readOnly = true)
    public GuestResponse getGuestById(final UUID id) {
        final UUID hotelId = extractHotelId();
        return guestMapper.toResponse(resolveGuest(id, hotelId));
    }

    /**
     * Retrieves a paginated list of active guests belonging to the caller's hotel.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of guest response DTOs scoped to the hotel
     */
    @Override
    @Transactional(readOnly = true)
    public Page<GuestResponse> getAllGuests(final Pageable pageable) {
        final UUID hotelId = extractHotelId();
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        return guestRepository.findAllByHotelId(hotelId, safePageable).map(guestMapper::toResponse);
    }

    /**
     * Updates an existing guest's profile, verifying hotel ownership.
     *
     * @param id      the guest UUID; must not be {@code null}
     * @param request the update request; must not be {@code null}
     * @return the updated guest as a response DTO
     * @throws NotFoundException if no guest with the given ID exists in this hotel
     */
    @Override
    @Transactional
    public GuestResponse updateGuest(final UUID id, final GuestRequest request) {
        final UUID hotelId = extractHotelId();
        final Guest guest = Objects.requireNonNull(resolveGuest(id, hotelId));
        guestMapper.updateEntityFromRequest(request, guest);
        final Guest savedGuest = Objects.requireNonNull(guestRepository.save(guest));
        return guestMapper.toResponse(savedGuest);
    }

    /**
     * Hard-deletes (anonymises) a guest profile after verifying hotel ownership,
     * the absence of active reservations, and the expiry of all legal holds
     * (T-GST-05).
     *
     * <p>Anonymisation strategy (preserves referential integrity):
     * <ul>
     *   <li>Name replaced with {@code GDPR / ERASED_<id-prefix>}</li>
     *   <li>All PII fields set to {@code null}</li>
     *   <li>Identity documents hard-deleted (orphanRemoval)</li>
     *   <li>{@code active} set to {@code false} (hides from all queries)</li>
     * </ul>
     *
     * @param id the guest UUID; must not be {@code null}
     * @throws IllegalStateException    if the guest has active reservations
     * @throws GdprLegalHoldException   if a TULPS or fiscal legal hold is active
     *                                  (HTTP 451)
     * @throws NotFoundException        if no guest with the given ID exists in this hotel
     */
    @Override
    @Transactional
    public void deleteGuest(final UUID id) {
        final UUID hotelId = extractHotelId();
        final Guest guest = resolveGuest(id, hotelId);

        if (reservationClient.hasActiveReservations(id)) {
            throw new IllegalStateException("GUEST_HAS_ACTIVE_RESERVATIONS");
        }

        verifyLegalHolds(id, hotelId);

        log.info("[GDPR] GUEST_ANONYMISE_START | guestId={} | hotelId={}", id, hotelId);
        guest.setFirstName(ANON_FIRST);
        guest.setLastName(ANON_LAST_PREFIX + id.toString().substring(0, 8));
        guest.setEmail(null);
        guest.setPhone(null);
        guest.setAddress(null);
        guest.setCity(null);
        guest.setCountry(null);
        guest.setDateOfBirth(null);
        guest.getIdentityDocuments().clear();
        guest.setActive(false);
        guestRepository.save(Objects.requireNonNull(guest));
        log.info("[GDPR] GUEST_ANONYMISED | guestId={}", id);
    }

    /**
     * Checks TULPS and fiscal legal holds for the given guest.
     * Throws {@link GdprLegalHoldException} (HTTP 451) if either is active.
     *
     * @param guestId the guest UUID
     * @param hotelId the hotel UUID
     */
    private void verifyLegalHolds(final UUID guestId, final UUID hotelId) {
        final GuestPrivacySettings settings =
                privacySettingsService.getOrCreateEntity(hotelId);
        final int retentionYears = Math.max(settings.getGuestRetentionYears(),
                GuestPrivacySettings.TULPS_MIN_YEARS);

        final GuestLastStayClientResponse stayInfo =
                stayServiceClient.getLastStayDate(guestId);
        final LocalDate tulpsExpiry = computeTulpsExpiry(stayInfo, retentionYears);

        final GuestInvoiceClientResponse invoiceInfo =
                billingServiceClient.getLastInvoiceDate(guestId);
        final LocalDate fiscalExpiry = computeFiscalExpiry(invoiceInfo);

        final boolean tulpsBlocked  = tulpsExpiry  != null && !LocalDate.now().isAfter(tulpsExpiry);
        final boolean fiscalBlocked = fiscalExpiry != null && !LocalDate.now().isAfter(fiscalExpiry);

        if (tulpsBlocked && fiscalBlocked) {
            final LocalDate unlocksAt = tulpsExpiry.isAfter(fiscalExpiry)
                    ? tulpsExpiry : fiscalExpiry;
            throw new GdprLegalHoldException(
                    "LEGAL_HOLD_ACTIVE: TULPS and fiscal obligations pending",
                    unlocksAt, LegalBasis.TULPS_AND_FISCAL);
        }
        if (tulpsBlocked) {
            throw new GdprLegalHoldException(
                    "LEGAL_HOLD_ACTIVE: TULPS obligation pending until " + tulpsExpiry,
                    tulpsExpiry, LegalBasis.TULPS);
        }
        if (fiscalBlocked) {
            throw new GdprLegalHoldException(
                    "LEGAL_HOLD_ACTIVE: fiscal obligation pending until " + fiscalExpiry,
                    fiscalExpiry, LegalBasis.FISCAL);
        }
    }

    private static LocalDate computeTulpsExpiry(
            final GuestLastStayClientResponse info, final int retentionYears) {
        if (!info.hasStays() || info.lastStayDate() == null) {
            return null;
        }
        return info.lastStayDate().plusYears(retentionYears);
    }

    private static LocalDate computeFiscalExpiry(final GuestInvoiceClientResponse info) {
        if (!info.hasInvoices() || info.lastInvoiceDate() == null) {
            return null;
        }
        return info.lastInvoiceDate().plusYears(GuestPrivacySettings.FISCAL_MIN_YEARS);
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
        final UUID hotelId = extractHotelId();
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        return guestRepository.searchByKeywordAndHotelId(safeQuery, hotelId, safePageable)
                .map(guestMapper::toResponse);
    }

    /**
     * Adds a new identity document to an existing guest, verifying hotel ownership.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param request the document creation request; must not be {@code null}
     * @return the persisted document as a response DTO
     * @throws NotFoundException if no guest with the given ID exists in this hotel
     */
    @Override
    @Transactional
    public IdentityDocumentResponseDTO addIdentityDocument(final UUID guestId,
            final IdentityDocumentRequestDTO request) {
        final UUID hotelId = extractHotelId();
        final Guest guest = resolveGuest(guestId, hotelId);
        final IdentityDocument document = identityDocumentMapper.toEntity(request);
        document.setGuest(guest);
        document.setActive(true);

        guest.getIdentityDocuments().add(document);
        final IdentityDocument saved = identityDocumentRepository.save(document);
        guestRepository.save(guest);

        return identityDocumentMapper.toResponse(saved);
    }

    /**
     * Removes an identity document from a guest, verifying hotel ownership and
     * document ownership.
     *
     * @param guestId    the guest UUID; must not be {@code null}
     * @param documentId the document UUID to remove; must not be {@code null}
     * @throws IllegalArgumentException if the document does not belong to the guest
     * @throws NotFoundException        if the guest or document is not found in this hotel
     */
    @Override
    @Transactional
    public void removeIdentityDocument(final UUID guestId, final UUID documentId) {
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        final UUID hotelId = extractHotelId();
        final Guest guest = resolveGuest(guestId, hotelId);
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
     * {@inheritDoc}
     *
     * <p>Only guests belonging to the caller's hotel are returned; IDs from other
     * hotels are silently excluded.
     */
    @Override
    @Transactional(readOnly = true)
    public List<GuestResponse> getGuestsByIds(final List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        final UUID hotelId = extractHotelId();
        return guestRepository.findAllByIdInAndHotelId(ids, hotelId).stream()
                .map(guestMapper::toResponse)
                .toList();
    }

    /**
     * Resolves a {@link Guest} entity by ID within the caller's hotel, or throws
     * {@link NotFoundException}. Returning 404 (rather than 403) when a guest
     * exists but belongs to a different hotel prevents IDOR enumeration.
     *
     * @param id      the guest UUID; must not be {@code null}
     * @param hotelId the owning hotel UUID; must not be {@code null}
     * @return the resolved guest entity
     * @throws NotFoundException if no active guest with the given ID exists in this hotel
     */
    private Guest resolveGuest(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, "Guest ID cannot be null");
        return guestRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(GUEST_NOT_FOUND_MSG));
    }

    /**
     * Extracts the hotel UUID from the current Spring Security context.
     * The value is stored as {@link Authentication#getDetails()} by
     * {@code InternalAuthFilter}, which reads it from the {@code X-Auth-Hotel}
     * header injected by the API Gateway.
     *
     * @return the hotel UUID for the authenticated caller
     * @throws IllegalStateException if no authentication is present or the hotel ID
     *                               is missing or malformed
     */
    private UUID extractHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        final Object details = auth.getDetails();
        if (!(details instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
