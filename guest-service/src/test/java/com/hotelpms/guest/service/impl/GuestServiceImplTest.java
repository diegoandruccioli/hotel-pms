package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.AlloggiatiComuniClient;
import com.hotelpms.guest.client.BillingServiceClient;
import com.hotelpms.guest.client.ReservationClient;
import com.hotelpms.guest.client.StayServiceClient;
import com.hotelpms.guest.client.dto.AlloggiatiComuneClientResponse;
import com.hotelpms.guest.client.dto.GuestInvoiceClientResponse;
import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import com.hotelpms.guest.exception.GuestValidationException;
import com.hotelpms.guest.exception.NotFoundException;
import com.hotelpms.guest.mapper.GuestMapper;
import com.hotelpms.guest.mapper.IdentityDocumentMapper;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.model.IdentityDocument;
import com.hotelpms.guest.model.enums.DocumentType;
import com.hotelpms.guest.service.GuestPrivacySettingsService;
import com.hotelpms.guest.repository.GuestRepository;
import com.hotelpms.guest.repository.IdentityDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GuestServiceImplTest {

    private static final String TEST_FIRST_NAME = "John";
    private static final String TEST_LAST_NAME = "Doe";
    private static final String TEST_EMAIL = "john.doe@example.com";
    private static final String TEST_PHONE = "1234567890";
    private static final String TEST_ADDRESS = "123 Main St";
    private static final String TEST_CITY = "Anytown";
    private static final String TEST_COUNTRY = "Country";
    private static final String DOC_NUMBER = "AB123456";

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private IdentityDocumentRepository identityDocumentRepository;

    @Mock
    private GuestMapper guestMapper;

    @Mock
    private IdentityDocumentMapper identityDocumentMapper;

    @Mock
    private ReservationClient reservationClient;

    @Mock
    private GuestPrivacySettingsService privacySettingsService;

    @Mock
    private StayServiceClient stayServiceClient;

    @Mock
    private BillingServiceClient billingServiceClient;

    @Mock
    private AlloggiatiComuniClient alloggiatiComuniClient;

    @InjectMocks
    private GuestServiceImpl guestService;

    private Guest guest;
    private GuestRequest guestRequest;
    private GuestResponse guestResponse;
    private UUID guestId;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
        hotelId = UUID.randomUUID();

        guest = Guest.builder()
                .id(guestId)
                .hotelId(hotelId)
                .firstName(TEST_FIRST_NAME)
                .lastName(TEST_LAST_NAME)
                .email(TEST_EMAIL)
                .phone(TEST_PHONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .active(true)
                .build();

        guestRequest = new GuestRequest(
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_EMAIL,
                TEST_PHONE,
                TEST_ADDRESS,
                TEST_CITY,
                TEST_COUNTRY,
                null,
                null, null, null, null, null,
                null, null, null);

        guestResponse = new GuestResponse(
                guestId,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_EMAIL,
                TEST_PHONE,
                TEST_ADDRESS,
                TEST_CITY,
                TEST_COUNTRY,
                null,
                null, null, null, null, null,
                null, null, null,
                Collections.emptyList(),
                null,
                guest.getCreatedAt(),
                guest.getUpdatedAt());

        final Authentication auth = mock(Authentication.class);
        lenient().when(auth.getDetails()).thenReturn(hotelId.toString());
        final SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateGuestSuccessfully() {
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);

        when(guestMapper.toEntity(guestRequest)).thenReturn(nonNullGuest);
        when(guestRepository.save(nonNullGuest)).thenReturn(nonNullGuest);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final GuestResponse result = guestService.createGuest(guestRequest);

        assertNotNull(result);
        assertEquals(guestId, result.id());
        assertEquals(TEST_FIRST_NAME, result.firstName());
        verify(guestRepository).save(nonNullGuest);
    }

    @Test
    void shouldCreateGuestWithValidComuneAndProvincia() {
        final GuestRequest requestWithAddress = new GuestRequest(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL,
                TEST_PHONE, TEST_ADDRESS, TEST_CITY, TEST_COUNTRY, null,
                null, null, null, null, null,
                "00100", "Roma", "RM");
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);

        when(alloggiatiComuniClient.searchComuni("Roma", "RM"))
                .thenReturn(List.of(new AlloggiatiComuneClientResponse("058091", "Roma", "RM")));
        when(guestMapper.toEntity(requestWithAddress)).thenReturn(nonNullGuest);
        when(guestRepository.save(nonNullGuest)).thenReturn(nonNullGuest);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final GuestResponse result = guestService.createGuest(requestWithAddress);

        assertNotNull(result);
        verify(guestRepository).save(nonNullGuest);
    }

    @Test
    void shouldRejectGuestWithComuneNotMatchingAnyMunicipality() {
        final GuestRequest requestWithBadComune = new GuestRequest(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL,
                null, null, null, null, null,
                null, null, null, null, null,
                null, "Cittainesistente", "RM");

        when(alloggiatiComuniClient.searchComuni("Cittainesistente", "RM")).thenReturn(List.of());

        assertThrows(GuestValidationException.class, () -> guestService.createGuest(requestWithBadComune));
        verify(guestRepository, never()).save(any());
    }

    @Test
    void shouldRejectGuestWithComuneButNoProvincia() {
        final GuestRequest requestWithoutProvincia = new GuestRequest(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL,
                null, null, null, null, null,
                null, null, null, null, null,
                null, "Roma", null);

        assertThrows(GuestValidationException.class, () -> guestService.createGuest(requestWithoutProvincia));
    }

    @Test
    void shouldGetGuestByIdSuccessfully() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);

        when(guestRepository.findByIdAndHotelId(nonNullGuestId, hotelId))
                .thenReturn(Optional.of(nonNullGuest));
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final GuestResponse result = guestService.getGuestById(nonNullGuestId);

        assertNotNull(result);
        assertEquals(guestId, result.id());
        assertEquals(TEST_EMAIL, result.email());
    }

    @Test
    void shouldThrowNotFoundExceptionWhenGuestNotFound() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);

        when(guestRepository.findByIdAndHotelId(nonNullGuestId, hotelId))
                .thenReturn(Optional.empty());

        final NotFoundException exception = assertThrows(NotFoundException.class,
                () -> guestService.getGuestById(nonNullGuestId));

        assertEquals("GUEST_NOT_FOUND", exception.getMessage());
    }

    @Test
    void shouldReturnNotFoundForGuestBelongingToOtherHotel() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);

        when(guestRepository.findByIdAndHotelId(nonNullGuestId, hotelId))
                .thenReturn(Optional.empty());

        final NotFoundException exception = assertThrows(NotFoundException.class,
                () -> guestService.getGuestById(nonNullGuestId));

        assertEquals("GUEST_NOT_FOUND", exception.getMessage());
    }

    @Test
    void shouldUpdateGuestSuccessfully() {
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);

        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.of(nonNullGuest));
        when(guestRepository.save(nonNullGuest)).thenReturn(nonNullGuest);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final GuestResponse result = guestService.updateGuest(guestId, guestRequest);

        assertNotNull(result);
        assertEquals(guestId, result.id());
        verify(guestMapper).updateEntityFromRequest(guestRequest, nonNullGuest);
        verify(guestRepository).save(nonNullGuest);
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingNonExistentGuest() {
        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> guestService.updateGuest(guestId, guestRequest));
    }

    @Test
    void shouldReturnAllGuestsPagedSuccessfully() {
        final Pageable pageable = PageRequest.of(0, 10);
        final Page<Guest> guestPage = new PageImpl<>(List.of(Objects.requireNonNull(guest)));

        when(guestRepository.findAllByHotelId(hotelId, pageable)).thenReturn(guestPage);
        when(guestMapper.toResponse(Objects.requireNonNull(guest))).thenReturn(guestResponse);

        final Page<GuestResponse> result = guestService.getAllGuests(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(TEST_FIRST_NAME, result.getContent().get(0).firstName());
    }

    @Test
    void shouldDeleteGuestSuccessfully() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestPrivacySettings settings = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS).build();

        when(guestRepository.findByIdAndHotelId(nonNullGuestId, hotelId))
                .thenReturn(Optional.of(nonNullGuest));
        when(reservationClient.hasActiveReservations(nonNullGuestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(settings);
        when(stayServiceClient.getLastStayDate(nonNullGuestId))
                .thenReturn(new GuestLastStayClientResponse(false, null));
        when(billingServiceClient.getLastInvoiceDate(nonNullGuestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));
        when(guestRepository.save(nonNullGuest)).thenReturn(nonNullGuest);

        guestService.deleteGuest(nonNullGuestId);

        verify(guestRepository).save(nonNullGuest);
    }

    @Test
    void shouldThrowWhenGuestHasActiveReservations() {
        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.of(Objects.requireNonNull(guest)));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> guestService.deleteGuest(guestId));
        verify(guestRepository, never()).delete(any());
    }

    @Test
    void shouldSearchGuestsWithQuery() {
        final String query = "John";
        final Pageable pageable = PageRequest.of(0, 10);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);
        final Page<Guest> guestPage = new PageImpl<>(Objects.requireNonNull(List.of(nonNullGuest)));

        when(guestRepository.searchByKeywordAndHotelId(query, hotelId, pageable)).thenReturn(guestPage);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final Page<GuestResponse> result = guestService.searchGuests(query, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(TEST_FIRST_NAME, result.getContent().get(0).firstName());
    }

    @Test
    void shouldSearchGuestsWithoutQuery() {
        final String query = "";
        final Pageable pageable = PageRequest.of(0, 10);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);
        final Page<Guest> guestPage = new PageImpl<>(Objects.requireNonNull(List.of(nonNullGuest)));

        when(guestRepository.findAllByHotelId(hotelId, pageable)).thenReturn(guestPage);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final Page<GuestResponse> result = guestService.searchGuests(query, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(TEST_FIRST_NAME, result.getContent().get(0).firstName());
    }

    @Test
    void shouldReturnGuestsByIds() {
        final List<UUID> ids = List.of(Objects.requireNonNull(guestId));

        when(guestRepository.findAllByIdInAndHotelId(ids, hotelId))
                .thenReturn(List.of(Objects.requireNonNull(guest)));
        when(guestMapper.toResponse(Objects.requireNonNull(guest))).thenReturn(guestResponse);

        final List<GuestResponse> result = guestService.getGuestsByIds(ids);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(TEST_FIRST_NAME, result.get(0).firstName());
    }

    @Test
    void shouldReturnEmptyListWhenIdsAreEmpty() {
        final List<GuestResponse> result = guestService.getGuestsByIds(Collections.emptyList());

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(guestRepository, never()).findAllByIdInAndHotelId(any(), any());
    }

    @Test
    void shouldAddIdentityDocumentSuccessfully() {
        final UUID docId = UUID.randomUUID();
        final IdentityDocumentRequestDTO docRequest = new IdentityDocumentRequestDTO(
                DocumentType.PASSPORT,
                DOC_NUMBER,
                LocalDate.now().minusYears(5),
                LocalDate.now().plusYears(5),
                "Italy");
        final IdentityDocument document = IdentityDocument.builder()
                .id(docId)
                .guest(guest)
                .documentType(DocumentType.PASSPORT)
                .documentNumber(DOC_NUMBER)
                .active(true)
                .build();
        final IdentityDocumentResponseDTO docResponse = new IdentityDocumentResponseDTO(
                docId, DocumentType.PASSPORT, DOC_NUMBER,
                LocalDate.now().minusYears(5), LocalDate.now().plusYears(5),
                "Italy", null, null);

        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.of(Objects.requireNonNull(guest)));
        when(identityDocumentMapper.toEntity(docRequest)).thenReturn(document);
        when(identityDocumentRepository.save(document)).thenReturn(document);
        when(identityDocumentMapper.toResponse(document)).thenReturn(docResponse);

        final IdentityDocumentResponseDTO result = guestService.addIdentityDocument(guestId, docRequest);

        assertNotNull(result);
        assertEquals(DOC_NUMBER, result.documentNumber());
        verify(identityDocumentRepository).save(document);
    }

    @Test
    void shouldRemoveIdentityDocumentSuccessfully() {
        final UUID docId = UUID.randomUUID();
        final IdentityDocument document = IdentityDocument.builder()
                .id(docId)
                .guest(guest)
                .documentType(DocumentType.PASSPORT)
                .documentNumber(DOC_NUMBER)
                .active(true)
                .build();
        Objects.requireNonNull(guest).getIdentityDocuments().add(document);

        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.of(Objects.requireNonNull(guest)));
        when(identityDocumentRepository.findById(docId)).thenReturn(Optional.of(document));

        guestService.removeIdentityDocument(guestId, docId);

        verify(identityDocumentRepository).delete(document);
        verify(guestRepository).save(Objects.requireNonNull(guest));
    }

    @Test
    void shouldThrowNotFoundWhenRemovingNonExistentDocument() {
        final UUID docId = UUID.randomUUID();

        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.of(Objects.requireNonNull(guest)));
        when(identityDocumentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> guestService.removeIdentityDocument(guestId, docId));
    }

    @Test
    void shouldThrowIllegalArgumentWhenDocumentBelongsToOtherGuest() {
        final UUID docId = UUID.randomUUID();
        final UUID otherGuestId = UUID.randomUUID();
        final Guest otherGuest = Guest.builder()
                .id(otherGuestId)
                .hotelId(hotelId)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .active(true)
                .build();
        final IdentityDocument document = IdentityDocument.builder()
                .id(docId)
                .guest(otherGuest)
                .documentType(DocumentType.PASSPORT)
                .documentNumber("XY999999")
                .active(true)
                .build();

        when(guestRepository.findByIdAndHotelId(Objects.requireNonNull(guestId), hotelId))
                .thenReturn(Optional.of(Objects.requireNonNull(guest)));
        when(identityDocumentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThrows(IllegalArgumentException.class,
                () -> guestService.removeIdentityDocument(guestId, docId));
    }
}
