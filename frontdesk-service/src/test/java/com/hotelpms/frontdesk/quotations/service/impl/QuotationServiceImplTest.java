package com.hotelpms.frontdesk.quotations.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.GuestCreateRequest;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.quotations.domain.Quotation;
import com.hotelpms.frontdesk.quotations.domain.QuotationLineItem;
import com.hotelpms.frontdesk.quotations.domain.QuotationOption;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import com.hotelpms.frontdesk.quotations.dto.QuotationOptionRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationResponse;
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
import java.util.ArrayList;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotationServiceImplTest {

    private static final UUID HOTEL_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID GUEST_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID ROOM_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID ROOM_TYPE_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID ROOM_ID_2 = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID ROOM_TYPE_ID_2 = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID QUOTATION_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID OPTION_ID = Objects.requireNonNull(UUID.randomUUID());
    private static final UUID OPTION_ID_2 = Objects.requireNonNull(UUID.randomUUID());
    private static final String ROOM_NUMBER = "101";
    private static final String ROOM_NUMBER_2 = "202";
    private static final String GUEST_FIRST_NAME = "Mario";
    private static final String GUEST_LAST_NAME = "Rossi";
    private static final String GUEST_EMAIL = "mario@example.com";
    private static final String HOTEL_NAME = "Hotel Test";
    private static final BigDecimal NIGHTLY_PRICE = BigDecimal.valueOf(100);
    private static final BigDecimal NIGHTLY_PRICE_2 = BigDecimal.valueOf(150);
    private static final BigDecimal SUITE_BASE_PRICE = BigDecimal.valueOf(140);
    private static final String ROOM_TYPE_NAME = "Standard";
    private static final String ROOM_TYPE_NAME_2 = "Suite";
    private static final String DEFAULT_OPTION_LABEL = "Opzione 1";
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(13);
    private static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(5);
    private static final byte[] PDF_BYTES = {1, 2, 3};

    @Mock
    private QuotationRepository quotationRepository;
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
        lenient().when(roomService.getRoomById(ROOM_ID, HOTEL_ID)).thenReturn(room());
        lenient().when(roomService.getRoomById(ROOM_ID_2, HOTEL_ID)).thenReturn(room2());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static RoomTypeResponse roomType() {
        return new RoomTypeResponse(ROOM_TYPE_ID, ROOM_TYPE_NAME, null, 2, BigDecimal.valueOf(90), true, null, null);
    }

    private static RoomTypeResponse roomType2() {
        return new RoomTypeResponse(ROOM_TYPE_ID_2, ROOM_TYPE_NAME_2, null, 3, SUITE_BASE_PRICE, true, null, null);
    }

    private static RoomResponse room() {
        return new RoomResponse(ROOM_ID, HOTEL_ID, ROOM_NUMBER, roomType(), RoomStatus.CLEAN, true, null, null, null);
    }

    private static RoomResponse room2() {
        return new RoomResponse(ROOM_ID_2, HOTEL_ID, ROOM_NUMBER_2, roomType2(), RoomStatus.CLEAN, true, null, null, null);
    }

    private static QuotationOptionRequest optionRequest(final String label, final UUID... roomIds) {
        return new QuotationOptionRequest(label, List.of(roomIds));
    }

    private static QuotationRequest requestWithOptions(final List<QuotationOptionRequest> options) {
        return new QuotationRequest(GUEST_ID, null, null, null, CHECK_IN, CHECK_OUT, 2, options, VALID_UNTIL);
    }

    /**
     * A single-option quotation entity, mirroring the pre-multi-option shape for existing tests.
     *
     * @param status     the quotation status
     * @param validUntil the quotation's valid-until date
     * @return the built entity
     */
    private static Quotation quotationEntity(final QuotationStatus status, final LocalDate validUntil) {
        return quotationEntityWithOptions(status, validUntil, List.of(DEFAULT_OPTION_LABEL));
    }

    private static Quotation quotationEntityWithOptions(
            final QuotationStatus status, final LocalDate validUntil, final List<String> optionLabels) {
        final BigDecimal optionTotal = NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3));
        final Quotation quotation = Quotation.builder()
                .id(QUOTATION_ID)
                .hotelId(HOTEL_ID)
                .guestId(GUEST_ID)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .expectedGuests(2)
                .status(status)
                .validUntil(validUntil)
                .totalPrice(optionTotal)
                .build();
        final List<QuotationOption> options = new ArrayList<>();
        int position = 0;
        for (final String label : optionLabels) {
            final QuotationOption option = QuotationOption.builder()
                    .id(position == 0 ? OPTION_ID : OPTION_ID_2)
                    .quotation(quotation)
                    .label(label)
                    .position(position)
                    .totalPrice(optionTotal)
                    .build();
            final QuotationLineItem lineItem = QuotationLineItem.builder()
                    .roomId(ROOM_ID)
                    .price(optionTotal)
                    .build();
            lineItem.setQuotation(quotation);
            lineItem.setQuotationOption(option);
            option.setLineItems(new ArrayList<>(List.of(lineItem)));
            options.add(option);
            position++;
        }
        quotation.setOptions(options);
        return quotation;
    }

    private void stubRates(final BigDecimal nightlyPrice) {
        when(ratePricingService.resolveStayRates(ROOM_TYPE_ID, HOTEL_ID, CHECK_IN, CHECK_OUT))
                .thenReturn(List.of(
                        new NightlyRate(CHECK_IN, nightlyPrice, null),
                        new NightlyRate(CHECK_IN.plusDays(1), nightlyPrice, null),
                        new NightlyRate(CHECK_IN.plusDays(2), nightlyPrice, null)));
    }

    private void stubRatesForRoomType2(final BigDecimal nightlyPrice) {
        when(ratePricingService.resolveStayRates(ROOM_TYPE_ID_2, HOTEL_ID, CHECK_IN, CHECK_OUT))
                .thenReturn(List.of(
                        new NightlyRate(CHECK_IN, nightlyPrice, null),
                        new NightlyRate(CHECK_IN.plusDays(1), nightlyPrice, null),
                        new NightlyRate(CHECK_IN.plusDays(2), nightlyPrice, null)));
    }

    @Test
    void createQuotationResolvesAndFreezesThePrice() {
        final GuestResponse guest = new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);
        stubRates(NIGHTLY_PRICE);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final QuotationRequest request = requestWithOptions(List.of(optionRequest(DEFAULT_OPTION_LABEL, ROOM_ID)));

        final QuotationResponse response = quotationService.createQuotation(request);

        assertNotNull(response);
        assertEquals(1, response.options().size());
        final BigDecimal expectedTotal = NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3));
        assertEquals(expectedTotal, response.totalPrice());
        assertEquals(expectedTotal, response.options().get(0).totalPrice());
        assertEquals(QuotationStatus.DRAFT, response.status());
        assertEquals(ROOM_NUMBER, response.options().get(0).lineItems().get(0).roomNumber());
    }

    @Test
    void createQuotationWithMultipleOptionsPricesEachIndependently() {
        final GuestResponse guest = new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);
        stubRates(NIGHTLY_PRICE);
        stubRatesForRoomType2(NIGHTLY_PRICE_2);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final QuotationRequest request = requestWithOptions(List.of(
                optionRequest(ROOM_TYPE_NAME, ROOM_ID),
                optionRequest(ROOM_TYPE_NAME_2, ROOM_ID_2)));

        final QuotationResponse response = quotationService.createQuotation(request);

        assertEquals(2, response.options().size());
        final BigDecimal standardTotal = NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3));
        final BigDecimal suiteTotal = NIGHTLY_PRICE_2.multiply(BigDecimal.valueOf(3));
        assertEquals(standardTotal, response.options().get(0).totalPrice());
        assertEquals(suiteTotal, response.options().get(1).totalPrice());
        // totalPrice at the top level is the lowest of the two options.
        assertEquals(standardTotal, response.totalPrice());
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

        final QuotationResponse response = quotationService.getQuotationById(QUOTATION_ID);

        assertEquals(QuotationStatus.EXPIRED, response.status());
    }

    @Test
    void updateQuotationOnDraftRecalculatesPriceAtCurrentRates() {
        final Quotation quotation = quotationEntity(QuotationStatus.DRAFT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        final BigDecimal newNightlyPrice = BigDecimal.valueOf(150);
        stubRates(newNightlyPrice);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final QuotationRequest request = requestWithOptions(List.of(optionRequest(DEFAULT_OPTION_LABEL, ROOM_ID)));

        final QuotationResponse response = quotationService.updateQuotation(QUOTATION_ID, request);

        assertEquals(newNightlyPrice.multiply(BigDecimal.valueOf(3)), response.totalPrice());
        assertEquals(1, quotation.getOptions().size());
        assertEquals(1, quotation.getOptions().get(0).getLineItems().size());
    }

    @Test
    void updateQuotationOnSentThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        final QuotationRequest request = requestWithOptions(List.of(optionRequest(DEFAULT_OPTION_LABEL, ROOM_ID)));

        assertThrows(ConflictException.class, () -> quotationService.updateQuotation(QUOTATION_ID, request));
        verify(quotationRepository, never()).save(any());
    }

    @Test
    void duplicateQuotationReResolvesPriceAtCurrentRatesNotTheFrozenOne() {
        final Quotation source = quotationEntity(QuotationStatus.SENT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(source));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        final BigDecimal currentNightlyPrice = BigDecimal.valueOf(200);
        stubRates(currentNightlyPrice);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final QuotationResponse response = quotationService.duplicateQuotation(QUOTATION_ID);

        assertEquals(currentNightlyPrice.multiply(BigDecimal.valueOf(3)), response.totalPrice());
        assertEquals(QuotationStatus.DRAFT, response.status());
        assertEquals(1, response.options().size());
    }

    @Test
    void sendQuotationEmailMarksSentOnFirstSuccessfulSend() {
        final Quotation quotation = quotationEntity(QuotationStatus.DRAFT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(notificationClient.sendQuotation(any())).thenReturn(true);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

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
        when(notificationClient.sendQuotation(any())).thenReturn(false);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

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
    void convertToReservationWithASingleOptionAndNoExplicitChoiceHonorsTheFrozenPrice() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        final ReservationResponse reservationResponse = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null,
                2, 0, CHECK_IN, CHECK_OUT, com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CONFIRMED,
                null, true, null, null, false, null);
        when(reservationService.createReservationFromPricedRooms(
                eq(GUEST_ID), eq(CHECK_IN), eq(CHECK_OUT), eq(2), any()))
                .thenReturn(reservationResponse);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final ReservationResponse result = quotationService.convertToReservation(QUOTATION_ID, null);

        assertNotNull(result);
        assertEquals(QuotationStatus.ACCEPTED, quotation.getStatus());
        assertEquals(OPTION_ID, quotation.getAcceptedOptionId());
        verify(guestClient, never()).createGuest(any());
    }

    @Test
    void convertToReservationWithMultipleOptionsAndNoExplicitChoiceThrowsBadRequest() {
        final Quotation quotation = quotationEntityWithOptions(
                QuotationStatus.SENT, VALID_UNTIL, List.of(ROOM_TYPE_NAME, ROOM_TYPE_NAME_2));
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(BadRequestException.class, () -> quotationService.convertToReservation(QUOTATION_ID, null));
        verify(reservationService, never()).createReservationFromPricedRooms(any(), any(), any(), any(), any());
    }

    @Test
    void convertToReservationWithAnUnknownOptionIdThrowsBadRequest() {
        final Quotation quotation = quotationEntityWithOptions(
                QuotationStatus.SENT, VALID_UNTIL, List.of(ROOM_TYPE_NAME, ROOM_TYPE_NAME_2));
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(BadRequestException.class,
                () -> quotationService.convertToReservation(QUOTATION_ID, UUID.randomUUID()));
    }

    @Test
    void convertToReservationWithExplicitOptionIdHonorsThatOptionsFrozenPrice() {
        final Quotation quotation = quotationEntityWithOptions(
                QuotationStatus.SENT, VALID_UNTIL, List.of(ROOM_TYPE_NAME, ROOM_TYPE_NAME_2));
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        final ReservationResponse reservationResponse = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null,
                2, 0, CHECK_IN, CHECK_OUT, com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CONFIRMED,
                null, true, null, null, false, null);
        when(reservationService.createReservationFromPricedRooms(
                eq(GUEST_ID), eq(CHECK_IN), eq(CHECK_OUT), eq(2), any()))
                .thenReturn(reservationResponse);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final ReservationResponse result = quotationService.convertToReservation(QUOTATION_ID, OPTION_ID_2);

        assertNotNull(result);
        assertEquals(OPTION_ID_2, quotation.getAcceptedOptionId());
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
        final QuotationOption option = QuotationOption.builder()
                .id(OPTION_ID).quotation(quotation).label(DEFAULT_OPTION_LABEL).position(0)
                .totalPrice(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3))).build();
        final QuotationLineItem lineItem = QuotationLineItem.builder()
                .roomId(ROOM_ID).price(NIGHTLY_PRICE.multiply(BigDecimal.valueOf(3))).build();
        lineItem.setQuotation(quotation);
        lineItem.setQuotationOption(option);
        option.setLineItems(List.of(lineItem));
        quotation.setOptions(List.of(option));

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

        final ReservationResponse result = quotationService.convertToReservation(QUOTATION_ID, null);

        assertNotNull(result);
        assertEquals(newGuestId, quotation.getGuestId());
        verify(guestClient).createGuest(new GuestCreateRequest(GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
    }

    @Test
    void convertToReservationExpiredThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, LocalDate.now().minusDays(1));
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(ConflictException.class, () -> quotationService.convertToReservation(QUOTATION_ID, null));
    }

    @Test
    void convertToReservationAlreadyAcceptedThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.ACCEPTED, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(ConflictException.class, () -> quotationService.convertToReservation(QUOTATION_ID, null));
    }

    @Test
    void declineQuotationSetsDeclinedStatus() {
        final Quotation quotation = quotationEntity(QuotationStatus.SENT, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));
        when(guestClient.getGuestById(GUEST_ID))
                .thenReturn(new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));

        final QuotationResponse response = quotationService.declineQuotation(QUOTATION_ID);

        assertEquals(QuotationStatus.DECLINED, response.status());
    }

    @Test
    void declineQuotationOnAcceptedThrowsConflict() {
        final Quotation quotation = quotationEntity(QuotationStatus.ACCEPTED, VALID_UNTIL);
        when(quotationRepository.findByIdAndHotelId(QUOTATION_ID, HOTEL_ID)).thenReturn(Optional.of(quotation));

        assertThrows(ConflictException.class, () -> quotationService.declineQuotation(QUOTATION_ID));
        verify(quotationRepository, never()).save(any());
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
                .options(List.of())
                .build();
        final Page<Quotation> page = new PageImpl<>(List.of(prospectQuotation));
        when(quotationRepository.findAllByHotelId(eq(HOTEL_ID), any())).thenReturn(page);

        final Page<QuotationResponse> result = quotationService.getAllQuotations(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(GUEST_FIRST_NAME + " " + GUEST_LAST_NAME, result.getContent().get(0).guestFullName());
        verify(guestClient, never()).getGuestsBatch(any());
    }
}
