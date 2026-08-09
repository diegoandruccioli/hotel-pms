package com.hotelpms.frontdesk.quotations.service.impl;

import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.GuestCreateRequest;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.NotificationQuotationRequest;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
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
import com.hotelpms.frontdesk.quotations.service.QuotationService;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of QuotationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuotationServiceImpl implements QuotationService {

    private static final String NOT_FOUND_MSG = "QUOTATION_NOT_FOUND";
    private static final String HOTEL_ID_NULL_MSG = "Hotel ID cannot be null";
    private static final String ID_NULL_MSG = "Quotation ID cannot be null";
    private static final String DEFAULT_CURRENCY = "EUR";
    private static final String DEFAULT_LOCALE = "it";
    private static final String NOTIFICATION_SERVICE_UNAVAILABLE_REASON = "NOTIFICATION_SERVICE_UNAVAILABLE";
    private static final String PDF_TEMPLATE = "quotation";
    private static final String PDF_FILE_PREFIX = "preventivo-";
    private static final String PDF_FILE_EXTENSION = ".pdf";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final QuotationRepository quotationRepository;
    private final QuotationMapper quotationMapper;
    private final RoomService roomService;
    private final RatePricingService ratePricingService;
    private final GuestClient guestClient;
    private final HotelSettingsService hotelSettingsService;
    private final NotificationClient notificationClient;
    private final ReservationService reservationService;
    private final PdfTemplateRenderer pdfTemplateRenderer;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationResponse createQuotation(final QuotationRequest request) {
        final UUID hotelId = resolveHotelId();
        final GuestResponse guest = request.guestId() != null ? verifyGuestExists(request.guestId()) : null;

        final Map<UUID, RoomResponse> roomsById = new HashMap<>();
        for (final UUID roomId : request.roomIds()) {
            roomsById.put(roomId, roomService.getRoomById(roomId, hotelId));
        }

        final List<QuotationLineItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (final UUID roomId : request.roomIds()) {
            final RoomResponse room = roomsById.get(roomId);
            final List<NightlyRate> nightlyRates = ratePricingService.resolveStayRates(
                    room.roomType().id(), hotelId, request.checkInDate(), request.checkOutDate());
            final BigDecimal price = nightlyRates.stream()
                    .map(NightlyRate::nightlyPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(price);
            lineItems.add(QuotationLineItem.builder().roomId(roomId).price(price).build());
        }

        final Quotation quotation = Quotation.builder()
                .hotelId(hotelId)
                .guestId(request.guestId())
                .prospectFirstName(request.guestId() == null ? request.prospectFirstName() : null)
                .prospectLastName(request.guestId() == null ? request.prospectLastName() : null)
                .prospectEmail(request.guestId() == null ? request.prospectEmail() : null)
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .expectedGuests(request.expectedGuests())
                .status(QuotationStatus.DRAFT)
                .validUntil(request.validUntil())
                .totalPrice(total)
                .build();
        lineItems.forEach(lineItem -> lineItem.setQuotation(quotation));
        quotation.setLineItems(lineItems);

        final Quotation saved = quotationRepository.save(quotation);
        return toResponse(saved, guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public QuotationResponse getQuotationById(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        return toResponse(quotation, guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<QuotationResponse> getAllQuotations(final Pageable pageable) {
        final UUID hotelId = resolveHotelId();
        final Page<Quotation> page = quotationRepository.findAllByHotelId(hotelId,
                pageable == null ? Pageable.unpaged() : pageable);

        final List<UUID> guestIds = page.getContent().stream()
                .map(Quotation::getGuestId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        final Map<UUID, GuestResponse> guestsById = guestIds.isEmpty()
                ? Map.of()
                : guestClient.getGuestsBatch(guestIds).stream()
                        .collect(java.util.stream.Collectors.toMap(GuestResponse::id, g -> g));

        return page.map(quotation -> toResponse(quotation,
                quotation.getGuestId() == null ? null : guestsById.get(quotation.getGuestId())));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public byte[] getQuotationPdf(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        return renderPdf(quotation, guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationResponse sendQuotationEmail(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        if (quotation.getStatus() == QuotationStatus.DECLINED) {
            throw new ConflictException("QUOTATION_DECLINED");
        }

        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        final String recipientEmail = guest != null ? guest.email() : quotation.getProspectEmail();
        final String recipientName = guest != null
                ? guest.firstName() + " " + guest.lastName()
                : quotation.getProspectFirstName() + " " + quotation.getProspectLastName();

        final byte[] pdf = renderPdf(quotation, guest);
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
        final boolean sent = notificationClient.sendQuotation(new NotificationQuotationRequest(
                recipientEmail,
                recipientName,
                settings.hotelName(),
                quotation.getCheckInDate(),
                quotation.getCheckOutDate(),
                quotation.getExpectedGuests(),
                quotation.getTotalPrice(),
                DEFAULT_CURRENCY,
                quotation.getValidUntil(),
                DEFAULT_LOCALE,
                settings.emailGreetingText(),
                settings.logoUrl(),
                pdf,
                PDF_FILE_PREFIX + quotation.getId() + PDF_FILE_EXTENSION));

        quotation.setSendFailed(!sent);
        quotation.setSendFailureReason(sent ? null : NOTIFICATION_SERVICE_UNAVAILABLE_REASON);
        if (sent && quotation.getStatus() == QuotationStatus.DRAFT) {
            quotation.setStatus(QuotationStatus.SENT);
        }
        final Quotation saved = quotationRepository.save(quotation);
        return toResponse(saved, guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse convertToReservation(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        if (quotation.getStatus() == QuotationStatus.DECLINED) {
            throw new ConflictException("QUOTATION_DECLINED");
        }
        if (quotation.getStatus() == QuotationStatus.ACCEPTED) {
            throw new ConflictException("QUOTATION_ALREADY_ACCEPTED");
        }
        if (quotation.isExpired()) {
            throw new ConflictException("QUOTATION_EXPIRED");
        }

        UUID guestId = quotation.getGuestId();
        if (guestId == null) {
            final GuestResponse created = guestClient.createGuest(new GuestCreateRequest(
                    quotation.getProspectFirstName(), quotation.getProspectLastName(), quotation.getProspectEmail()));
            guestId = created.id();
            quotation.setGuestId(guestId);
        }

        final Map<UUID, BigDecimal> roomPrices = quotation.getLineItems().stream()
                .collect(java.util.stream.Collectors.toMap(QuotationLineItem::getRoomId, QuotationLineItem::getPrice));

        final ReservationResponse reservation = reservationService.createReservationFromPricedRooms(
                guestId, quotation.getCheckInDate(), quotation.getCheckOutDate(),
                quotation.getExpectedGuests(), roomPrices);

        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotationRepository.save(quotation);
        return reservation;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationResponse declineQuotation(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        quotation.setStatus(QuotationStatus.DECLINED);
        final Quotation saved = quotationRepository.save(quotation);
        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        return toResponse(saved, guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteQuotation(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        quotationRepository.delete(quotation); // Triggers the @SQLDelete soft delete
    }

    private Quotation findByIdAndHotelOrThrow(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        return quotationRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG));
    }

    private GuestResponse verifyGuestExists(final UUID guestId) {
        try {
            return guestClient.getGuestById(guestId);
        } catch (final FeignException.NotFound e) {
            throw new BadRequestException("GUEST_NOT_FOUND", e);
        } catch (final FeignException e) {
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE", e);
        }
    }

    private byte[] renderPdf(final Quotation quotation, final GuestResponse guest) {
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(quotation.getHotelId());
        final Map<String, Object> context = new HashMap<>();
        context.put("docTitle", "PREVENTIVO");
        context.put("hotelName", settings.hotelName() != null ? settings.hotelName() : "Hotel");
        context.put("issueDate", LocalDate.now().format(DATE_FMT));
        context.put("checkInDate", quotation.getCheckInDate().format(DATE_FMT));
        context.put("checkOutDate", quotation.getCheckOutDate().format(DATE_FMT));
        context.put("expectedGuests", quotation.getExpectedGuests());
        context.put("guestDisplayName", guest != null
                ? guest.firstName() + " " + guest.lastName()
                : quotation.getProspectFirstName() + " " + quotation.getProspectLastName());
        context.put("rooms", toRoomRows(quotation.getLineItems()));
        context.put("totalFormatted", formatAmount(quotation.getTotalPrice()));
        context.put("validUntil", quotation.getValidUntil().format(DATE_FMT));
        return pdfTemplateRenderer.render(PDF_TEMPLATE, context);
    }

    private List<Map<String, String>> toRoomRows(final List<QuotationLineItem> lineItems) {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final QuotationLineItem lineItem : lineItems) {
            final Map<String, String> row = new HashMap<>();
            final RoomResponse room = roomService.getRoomById(lineItem.getRoomId(), lineItem.getQuotation().getHotelId());
            row.put("label", room.roomNumber() + " — " + room.roomType().name());
            row.put("priceFormatted", formatAmount(lineItem.getPrice()));
            rows.add(row);
        }
        return rows;
    }

    private static String formatAmount(final BigDecimal amount) {
        return amount == null ? "EUR 0,00" : String.format("EUR %,.2f", amount);
    }

    private QuotationResponse toResponse(final Quotation quotation, final GuestResponse guest) {
        final String guestFullName = guest != null
                ? guest.firstName() + " " + guest.lastName()
                : quotation.getGuestId() != null
                        ? "Unknown Guest"
                        : quotation.getProspectFirstName() + " " + quotation.getProspectLastName();
        final QuotationStatus effectiveStatus = quotation.isExpired() ? QuotationStatus.EXPIRED : quotation.getStatus();
        final String prospectEmail = quotation.getGuestId() == null ? quotation.getProspectEmail() : null;

        return new QuotationResponse(
                quotation.getId(),
                quotation.getGuestId(),
                guestFullName,
                prospectEmail,
                quotation.getCheckInDate(),
                quotation.getCheckOutDate(),
                quotation.getExpectedGuests(),
                effectiveStatus,
                quotation.getValidUntil(),
                quotation.getTotalPrice(),
                quotationMapper.toLineItemResponses(quotation.getLineItems()),
                quotation.isSendFailed(),
                quotation.getSendFailureReason(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt());
    }

    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }
}
