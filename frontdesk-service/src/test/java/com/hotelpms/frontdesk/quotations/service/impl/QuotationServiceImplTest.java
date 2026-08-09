package com.hotelpms.frontdesk.quotations.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.GuestCreateRequest;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.quotations.domain.Quotation;
import com.hotelpms.frontdesk.quotations.domain.QuotationLineItem;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import com.hotelpms.frontdesk.quotations.dto.QuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationResponse;
import com.hotelpms.frontdesk.quotations.mapper.QuotationMapper;
import com.hotelpms.frontdesk.quotations.repository.QuotationRepository;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotationServiceImplTest {

    private static final UUID HOTEL_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID GUEST_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID ROOM_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID ROOM_TYPE_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID QUOTATION_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final String ROOM_NUMBER = "101";
    private static final String GUEST_FIRST_NAME = "Mario";
    private static final String GUEST_LAST_NAME = "Rossi";
    private static final String GUEST_EMAIL = "mario@example.com";
    private static final String HOTEL_NAME = "Hotel Test";
    private static final BigDecimal NIGHTLY_PRICE = BigDecimal.valueOf(100);
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(13);
    private static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(5);
    private static final byte[] PDF_BYTES = {1, 2, 3};

    @Mock
    private QuotationRepository quotationRepository;
    @Mock
    private QuotationMapper quotationMapper;
    @Mock
    private RoomService roomService;
    @Mock
    private RatePricingService ratePricingService;
    @Mock
    private GuestClient guestClient;
    @Mock
    private HotelSettingsService hotelSettingsService;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private ReservationService reservationService;
    @Mock
    private PdfTemplateRenderer pdfTemplateRenderer;

    @InjectMocks
    private QuotationServiceImpl quotationService;

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        lenient().when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(
                new HotelSettingsResponse(HOTEL_ID, false, HOTEL_NAME, null, null, null, null, null, false,
                        true, true, null, null, null, null, null, null));
        lenient().when(pdfTemplateRenderer.render(eq("quotation"), any()))
                .thenReturn(PDF_BYTES);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static RoomTypeResponse roomType() {
        return new RoomTypeResponse(ROOM_TYPE_ID, "Standard", null, 2, BigDecimal.valueOf(90), true, null, null);
    }

    private static RoomResponse room() {
        return new RoomResponse(ROOM_ID, HOTEL_ID, ROOM_NUMBER, roomType(), RoomStatus.CLEAN, true, null, null, null);
    }

    private static Quotation quotationEntity(final QuotationStatus status, final LocalDate validUntil) {
        final Quotation quotation = Quotation.builder()
                .id(QUOTATION_ID)
                .hotelId(HOTEL_ID)
                .guestId(GUEST_ID)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .expectedGuests(2)
                .status(status)
                .validUntil(validUntil)
                .totalPrice(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3)))
                .build();
        final QuotationLineItem lineItem = QuotationLineItem.builder()
                .roomId(ROOM_ID)
                .price(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3)))
                .build();
        lineItem.setQuotation(quotation);
        quotation.setLineItems(List.of(lineItem));
        return quotation;
    }

    @Test
    void createQuotationResolvesAndFreezesThePrice() {
        final GuestResponse guest = new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);
        when(roomService.getRoomById(ROOM_ID, HOTEL_ID)).thenReturn(room());
        when(ratePricingService.resolveStayRates(ROOM_TYPE_ID, HOTEL_ID, CHECK_IN, CHECK_OUT))
                .thenReturn(List.of(
                        new NightlyRate(CHECK_IN, NIGHTLY_PRICE, null),
                        new NightlyRate(CHECK_IN.plusDays(1), NIGHTLY_PRICE, null),
                        new NightlyRate(CHECK_IN.plusDays(2), NIGHTLY_PRICE, null)));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final QuotationRequest request = new QuotationRequest(GUEST_ID, null, null, null,
                CHECK_IN, CHECK_OUT, 2, List.of(ROOM_ID), VALID_UNTIL);

        final QuotationResponse response = quotationService.createQuotation(request);

        assertNotNull(response);
        assertEquals(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3)), response.totalPrice());
        assertEquals(QuotationStatus.DRAFT, response.status());
    }

    @Test
    void quotationByIdNotFoundThrows() {
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> quotationService.getQuotationById(QUOTATION_ID));
    }

    @Test
    void quotationByIdPastValidUntilReturnsEffectiveExpiredStatus() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, LocalDate.now().minusDays(1));
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final QuotationResponse response = quotationService.getQuotationById(QUOTATION_ID);

        assertEquals(QuotationStatus.EXPIRED, response.status());
    }

    @Test
    void sendQuotationEmailMarksSentOnFirstSuccessfulSend() {
        final Quotation quotation = quotationEntity(QuotationStatus.DRAFT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(roomService.getRoomById(ROOM_ID, HOTEL_ID)).thenReturn(room());
        when(notificationClient.sendQuotation(any())).thenReturn(true);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final QuotationResponse response = quotationService.sendQuotationEmail(QUOTATION_ID);

        assertEquals(QuotationStatus.SENT, response.status());
        verify(notificationClient).sendQuotation(any());
    }

    @Test
    void sendQuotationEmailMarksFailedWhenNotificationServiceUnavailable() {
        final Quotation quotation = quotationEntity(QuotationStatus.DRAFT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(roomService.getRoomById(ROOM_ID, HOTEL_ID)).thenReturn(room());
        when(notificationClient.sendQuotation(any())).thenReturn(false);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final QuotationResponse response = quotationService.sendQuotationEmail(QUOTATION_ID);

        assertEquals(QuotationStatus.DRAFT, response.status());
        assertNotNull(response.sendFailureReason());
    }

    @Test
    void sendQuotationEmailOnDeclinedQuotationThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.DECLINED, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(ConflictException.class, () -> quotationService.sendQuotationEmail(QUOTATION_ID));
    }

    @Test
    void convertToReservationHonorsTheFrozenPriceForAnExistingGuest() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        final ReservationResponse reservationResponse = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null,
                2, 0, CHECK_IN, CHECK_OUT, com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CONFIRMED,
                null, true, null, null, false, null);
        when(reservationService.createReservationFromPricedRooms(
                eq(GUEST_ID), eq(CHECK_IN), eq(CHECK_OUT), eq(2), any()))
                .thenReturn(reservationResponse);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final ReservationResponse result = quotationService.convertToReservation(QUOTATION_ID);

        assertNotNull(result);
        assertEquals(QuotationStatus.ACCEPTED, quotation.getStatus());
        verify(guestClient, org.mockito.Mockito.never()).createGuest(any());
    }

    @Test
    void convertToReservationForAProspectCreatesAGuestFirst() {
        final Quotation quotation = Quotation.builder()
                .id(QUOTATION_ID)
                .hotelId(HOTEL_ID)
                .prospectFirstName(GUEST_FIRST_NAME)
                .prospectLastName(GUEST_LAST_NAME)
                .prospectEmail(GUEST_EMAIL)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .expectedGuests(2)
                .status(QuotationStatus.SENT)
                .validUntil(VALID_UNTIL)
                .totalPrice(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3)))
                .build();
        final QuotationLineItem lineItem = QuotationLineItem.builder()
                .roomId(ROOM_ID).price(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3))).build();
        lineItem.setQuotation(quotation);
        quotation.setLineItems(List.of(lineItem));

        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        final UUID newGuestId = UUID.randomUUID();
        when(guestClient.createGuest(new GuestCreateRequest(GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL)))
                .thenReturn(new GuestResponse(newGuestId, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        final ReservationResponse reservationResponse = new ReservationResponse(UUID.randomUUID(), newGuestId, null,
                2, 0, CHECK_IN, CHECK_OUT, com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CONFIRMED,
                null, true, null, null, false, null);
        when(reservationService.createReservationFromPricedRooms(
                eq(newGuestId), eq(CHECK_IN), eq(CHECK_OUT), eq(2), any()))
                .thenReturn(reservationResponse);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final ReservationResponse result = quotationService.convertToReservation(QUOTATION_ID);

        assertNotNull(result);
        assertEquals(newGuestId, quotation.getGuestId());
        verify(guestClient).createGuest(new GuestCreateRequest(GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
    }

    @Test
    void convertToReservationExpiredThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, LocalDate.now().minusDays(1));
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(ConflictException.class, () -> quotationService.convertToReservation(QUOTATION_ID));
    }

    @Test
    void convertToReservationAlreadyAcceptedThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.ACCEPTED, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(ConflictException.class, () -> quotationService.convertToReservation(QUOTATION_ID));
    }

    @Test
    void declineQuotationSetsDeclinedStatus() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final QuotationResponse response = quotationService.declineQuotation(QUOTATION_ID);

        assertEquals(QuotationStatus.DECLINED, response.status());
    }

    @Test
    void deleteQuotationSoftDeletes() {
        final Quotation quotation = quotationEntity(QuotationStatus.DRAFT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        quotationService.deleteQuotation(QUOTATION_ID);

        verify(quotationRepository).delete(quotation);
    }

    @Test
    void allQuotationsBatchResolvesGuestNames() {
        final Quotation quotation = quotationEntity(QuotationStatus.DRAFT, VALID_UNTIL);
        final Page<Quotation> page = new PageImpl<>(List.of(quotation));
        when(quotationRepository.findAllByHotelId(eq(HOTEL_ID), any())).thenReturn(page);
        when(guestClient.getGuestsBatch(List.of(GUEST_ID)))
                .thenReturn(List.of(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL)));
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final Page<QuotationResponse> result = quotationService.getAllQuotations(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(GUEST_FIRST_NAME + " " + GUEST_LAST_NAME, result.getContent().get(0).guestFullName());
    }

    @Test
    void allQuotationsWithAProspectOnlyRowDoesNotThrow() {
        // Regression test: a prospect quotation (guestId == null) among the results
        // must not NPE when resolving guest names — Map.of().get(null) throws.
        final Quotation prospectQuotation = Quotation.builder()
                .id(QUOTATION_ID)
                .hotelId(HOTEL_ID)
                .prospectFirstName(GUEST_FIRST_NAME)
                .prospectLastName(GUEST_LAST_NAME)
                .prospectEmail(GUEST_EMAIL)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .status(QuotationStatus.DRAFT)
                .validUntil(VALID_UNTIL)
                .totalPrice(NIGHTLY_PRICE)
                .lineItems(List.of())
                .build();
        final Page<Quotation> page = new PageImpl<>(List.of(prospectQuotation));
        when(quotationRepository.findAllByHotelId(eq(HOTEL_ID), any())).thenReturn(page);
        when(quotationMapper.toLineItemResponses(any())).thenReturn(List.of());

        final Page<QuotationResponse> result = quotationService.getAllQuotations(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(GUEST_FIRST_NAME + " " + GUEST_LAST_NAME, result.getContent().get(0).guestFullName());
        verify(guestClient, org.mockito.Mockito.never()).getGuestsBatch(any());
    }
}
