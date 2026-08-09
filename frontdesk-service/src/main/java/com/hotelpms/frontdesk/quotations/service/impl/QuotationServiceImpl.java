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
import com.hotelpms.frontdesk.quotations.domain.QuotationOption;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import com.hotelpms.frontdesk.quotations.dto.QuotationLineItemResponse;
import com.hotelpms.frontdesk.quotations.dto.QuotationOptionRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationOptionResponse;
import com.hotelpms.frontdesk.quotations.dto.QuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationResponse;
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
import java.util.Comparator;
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
    private static final int DUPLICATE_VALID_DAYS = 7;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final QuotationRepository quotationRepository;
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
        final Map<UUID, RoomResponse> roomsById = resolveRoomsForOptionRequests(request.options(), hotelId);
        final List<ResolvedOption> resolved = resolveOptions(
                request.options(), roomsById, hotelId, request.checkInDate(), request.checkOutDate());

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
                .totalPrice(minTotal(resolved))
                .build();
        applyOptions(quotation, resolved);

        final Quotation saved = quotationRepository.save(quotation);
        return toResponse(saved, guest, roomsById);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationResponse updateQuotation(final UUID id, final QuotationRequest request) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new ConflictException("QUOTATION_NOT_EDITABLE");
        }

        final GuestResponse guest = request.guestId() != null ? verifyGuestExists(request.guestId()) : null;
        final Map<UUID, RoomResponse> roomsById = resolveRoomsForOptionRequests(request.options(), hotelId);
        final List<ResolvedOption> resolved = resolveOptions(
                request.options(), roomsById, hotelId, request.checkInDate(), request.checkOutDate());

        quotation.setGuestId(request.guestId());
        quotation.setProspectFirstName(request.guestId() == null ? request.prospectFirstName() : null);
        quotation.setProspectLastName(request.guestId() == null ? request.prospectLastName() : null);
        quotation.setProspectEmail(request.guestId() == null ? request.prospectEmail() : null);
        quotation.setCheckInDate(request.checkInDate());
        quotation.setCheckOutDate(request.checkOutDate());
        quotation.setExpectedGuests(request.expectedGuests());
        quotation.setValidUntil(request.validUntil());
        quotation.setTotalPrice(minTotal(resolved));
        applyOptions(quotation, resolved);

        final Quotation saved = quotationRepository.save(quotation);
        return toResponse(saved, guest, roomsById);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationResponse duplicateQuotation(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation source = findByIdAndHotelOrThrow(id, hotelId);
        final List<UUID> allRoomIds = source.getOptions().stream()
                .flatMap(option -> option.getLineItems().stream().map(QuotationLineItem::getRoomId))
                .distinct()
                .toList();
        final Map<UUID, RoomResponse> roomsById = resolveRooms(allRoomIds, hotelId);

        final List<ResolvedOption> resolved = new ArrayList<>();
        for (final QuotationOption sourceOption : source.getOptions()) {
            final List<UUID> roomIds = sourceOption.getLineItems().stream()
                    .map(QuotationLineItem::getRoomId)
                    .toList();
            final ResolvedLineItems priced = resolveLineItems(
                    roomIds, roomsById, hotelId, source.getCheckInDate(), source.getCheckOutDate());
            resolved.add(new ResolvedOption(sourceOption.getLabel(), priced.lineItems(), priced.total()));
        }

        final LocalDate proposedValidUntil = LocalDate.now().plusDays(DUPLICATE_VALID_DAYS);
        final LocalDate validUntil = proposedValidUntil.isBefore(source.getCheckInDate())
                ? proposedValidUntil : source.getCheckInDate();

        final Quotation duplicate = Quotation.builder()
                .hotelId(hotelId)
                .guestId(source.getGuestId())
                .prospectFirstName(source.getProspectFirstName())
                .prospectLastName(source.getProspectLastName())
                .prospectEmail(source.getProspectEmail())
                .checkInDate(source.getCheckInDate())
                .checkOutDate(source.getCheckOutDate())
                .expectedGuests(source.getExpectedGuests())
                .status(QuotationStatus.DRAFT)
                .validUntil(validUntil)
                .totalPrice(minTotal(resolved))
                .build();
        applyOptions(duplicate, resolved);

        final Quotation saved = quotationRepository.save(duplicate);
        final GuestResponse guest = saved.getGuestId() != null
                ? guestClient.getGuestById(saved.getGuestId()) : null;
        return toResponse(saved, guest, roomsById);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public QuotationResponse getQuotationById(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        return toResponse(quotation, guest, resolveRoomsForQuotation(quotation, hotelId));
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

        return page.map(quotation -> toResponse(
                quotation,
                quotation.getGuestId() == null ? null : guestsById.get(quotation.getGuestId()),
                resolveRoomsForQuotation(quotation, hotelId)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public byte[] getQuotationPdf(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        return renderPdf(quotation, guest, resolveRoomsForQuotation(quotation, hotelId));
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
        final Map<UUID, RoomResponse> roomsById = resolveRoomsForQuotation(quotation, hotelId);
        final String recipientEmail = guest != null ? guest.email() : quotation.getProspectEmail();
        final String recipientName = guest != null
                ? guest.firstName() + " " + guest.lastName()
                : quotation.getProspectFirstName() + " " + quotation.getProspectLastName();

        final byte[] pdf = renderPdf(quotation, guest, roomsById);
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
        final boolean sent = notificationClient.sendQuotation(new NotificationQuotationRequest(
                recipientEmail,
                recipientName,
                settings.hotelName(),
                quotation.getCheckInDate(),
                quotation.getCheckOutDate(),
                quotation.getExpectedGuests(),
                quotation.getTotalPrice(),
                quotation.getOptions().size(),
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
        return toResponse(saved, guest, roomsById);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReservationResponse convertToReservation(final UUID id, final UUID optionId) {
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
        final QuotationOption chosen = resolveChosenOption(quotation, optionId);

        UUID guestId = quotation.getGuestId();
        if (guestId == null) {
            final GuestResponse created = guestClient.createGuest(new GuestCreateRequest(
                    quotation.getProspectFirstName(), quotation.getProspectLastName(), quotation.getProspectEmail()));
            guestId = created.id();
            quotation.setGuestId(guestId);
        }

        final Map<UUID, BigDecimal> roomPrices = chosen.getLineItems().stream()
                .collect(java.util.stream.Collectors.toMap(QuotationLineItem::getRoomId, QuotationLineItem::getPrice));

        final ReservationResponse reservation = reservationService.createReservationFromPricedRooms(
                guestId, quotation.getCheckInDate(), quotation.getCheckOutDate(),
                quotation.getExpectedGuests(), roomPrices);

        quotation.setAcceptedOptionId(chosen.getId());
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotationRepository.save(quotation);
        return reservation;
    }

    /**
     * Resolves which option a conversion applies to: the explicit
     * {@code optionId} if given (validated to belong to this quotation), or
     * the quotation's only option if it has exactly one.
     *
     * @param quotation the quotation being converted
     * @param optionId  the client-chosen option id, or {@code null}
     * @return the chosen option
     */
    private QuotationOption resolveChosenOption(final Quotation quotation, final UUID optionId) {
        final List<QuotationOption> options = quotation.getOptions();
        if (optionId == null) {
            if (options.size() == 1) {
                return options.get(0);
            }
            throw new BadRequestException("QUOTATION_OPTION_REQUIRED");
        }
        return options.stream()
                .filter(option -> option.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("QUOTATION_OPTION_NOT_FOUND"));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationResponse declineQuotation(final UUID id) {
        final UUID hotelId = resolveHotelId();
        final Quotation quotation = findByIdAndHotelOrThrow(id, hotelId);
        if (quotation.getStatus() == QuotationStatus.ACCEPTED) {
            throw new ConflictException("QUOTATION_ALREADY_ACCEPTED");
        }
        quotation.setStatus(QuotationStatus.DECLINED);
        final Quotation saved = quotationRepository.save(quotation);
        final GuestResponse guest = quotation.getGuestId() != null
                ? guestClient.getGuestById(quotation.getGuestId()) : null;
        return toResponse(saved, guest, resolveRoomsForQuotation(quotation, hotelId));
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

    // ------------------------------------------------------------------
    // Price resolution — shared by create/update/duplicate
    // ------------------------------------------------------------------

    /**
     * Resolves and freezes the price of every room in {@code roomIds} for the
     * given stay.
     *
     * @param roomIds   the rooms to price
     * @param roomsById each room's details, already resolved (see {@link #resolveRooms})
     * @param hotelId   the caller's hotel
     * @param checkIn   the stay's check-in date
     * @param checkOut  the stay's check-out date (exclusive)
     * @return the priced line items and their total
     */
    private ResolvedLineItems resolveLineItems(
            final List<UUID> roomIds, final Map<UUID, RoomResponse> roomsById, final UUID hotelId,
            final LocalDate checkIn, final LocalDate checkOut) {
        final List<QuotationLineItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (final UUID roomId : roomIds) {
            final RoomResponse room = roomsById.get(roomId);
            final List<NightlyRate> nightlyRates = ratePricingService.resolveStayRates(
                    room.roomType().id(), hotelId, checkIn, checkOut);
            final BigDecimal price = nightlyRates.stream()
                    .map(NightlyRate::nightlyPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(price);
            lineItems.add(QuotationLineItem.builder().roomId(roomId).price(price).build());
        }
        return new ResolvedLineItems(lineItems, total);
    }

    /**
     * Resolves every option in a request: one {@link ResolvedLineItems} pass
     * per option, each keeping its own label and total.
     *
     * @param optionRequests the requested options
     * @param roomsById      every room referenced by any option, already resolved
     * @param hotelId        the caller's hotel
     * @param checkIn        the stay's check-in date
     * @param checkOut       the stay's check-out date (exclusive)
     * @return the priced options, in request order
     */
    private List<ResolvedOption> resolveOptions(
            final List<QuotationOptionRequest> optionRequests, final Map<UUID, RoomResponse> roomsById,
            final UUID hotelId, final LocalDate checkIn, final LocalDate checkOut) {
        final List<ResolvedOption> resolved = new ArrayList<>();
        for (final QuotationOptionRequest optionRequest : optionRequests) {
            final ResolvedLineItems priced = resolveLineItems(
                    optionRequest.roomIds(), roomsById, hotelId, checkIn, checkOut);
            resolved.add(new ResolvedOption(optionRequest.label(), priced.lineItems(), priced.total()));
        }
        return resolved;
    }

    private Map<UUID, RoomResponse> resolveRooms(final List<UUID> roomIds, final UUID hotelId) {
        final Map<UUID, RoomResponse> roomsById = new HashMap<>();
        for (final UUID roomId : roomIds) {
            roomsById.computeIfAbsent(roomId, id -> roomService.getRoomById(id, hotelId));
        }
        return roomsById;
    }

    private Map<UUID, RoomResponse> resolveRoomsForOptionRequests(
            final List<QuotationOptionRequest> optionRequests, final UUID hotelId) {
        final List<UUID> allRoomIds = optionRequests.stream()
                .flatMap(option -> option.roomIds().stream())
                .distinct()
                .toList();
        return resolveRooms(allRoomIds, hotelId);
    }

    private Map<UUID, RoomResponse> resolveRoomsForQuotation(final Quotation quotation, final UUID hotelId) {
        final List<UUID> roomIds = quotation.getOptions().stream()
                .flatMap(option -> option.getLineItems().stream().map(QuotationLineItem::getRoomId))
                .distinct()
                .toList();
        return resolveRooms(roomIds, hotelId);
    }

    /**
     * Replaces {@code quotation}'s options in place with {@code resolved} —
     * mutates the managed collection (clear then rebuild) rather than
     * assigning a new list, so {@code orphanRemoval} correctly deletes
     * whatever options/line items an update removed. Works for a brand-new
     * quotation too, since {@code options} starts empty either way.
     *
     * @param quotation the quotation (new or existing) to attach options to
     * @param resolved  the priced options to attach, in display order
     */
    private void applyOptions(final Quotation quotation, final List<ResolvedOption> resolved) {
        quotation.getOptions().clear();
        int position = 0;
        for (final ResolvedOption resolvedOption : resolved) {
            final QuotationOption option = QuotationOption.builder()
                    .quotation(quotation)
                    .label(resolvedOption.label())
                    .position(position)
                    .totalPrice(resolvedOption.total())
                    .build();
            position++;
            for (final QuotationLineItem lineItem : resolvedOption.lineItems()) {
                lineItem.setQuotation(quotation);
                lineItem.setQuotationOption(option);
            }
            option.setLineItems(resolvedOption.lineItems());
            quotation.getOptions().add(option);
        }
    }

    private static BigDecimal minTotal(final List<ResolvedOption> resolved) {
        return resolved.stream()
                .map(ResolvedOption::total)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // PDF rendering
    // ------------------------------------------------------------------

    private byte[] renderPdf(
            final Quotation quotation, final GuestResponse guest, final Map<UUID, RoomResponse> roomsById) {
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
        context.put("options", toOptionRows(quotation.getOptions(), roomsById));
        context.put("multipleOptions", quotation.getOptions().size() > 1);
        context.put("validUntil", quotation.getValidUntil().format(DATE_FMT));
        return pdfTemplateRenderer.render(PDF_TEMPLATE, context);
    }

    private List<Map<String, Object>> toOptionRows(
            final List<QuotationOption> options, final Map<UUID, RoomResponse> roomsById) {
        final List<Map<String, Object>> rows = new ArrayList<>();
        final List<QuotationOption> sorted = options.stream()
                .sorted(Comparator.comparingInt(QuotationOption::getPosition))
                .toList();
        for (final QuotationOption option : sorted) {
            final Map<String, Object> row = new HashMap<>();
            row.put("label", option.getLabel());
            row.put("rooms", toRoomRows(option.getLineItems(), roomsById));
            row.put("totalFormatted", formatAmount(option.getTotalPrice()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> toRoomRows(
            final List<QuotationLineItem> lineItems, final Map<UUID, RoomResponse> roomsById) {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final QuotationLineItem lineItem : lineItems) {
            final RoomResponse room = roomsById.get(lineItem.getRoomId());
            final Map<String, String> row = new HashMap<>();
            row.put("label", room.roomNumber() + " — " + room.roomType().name());
            row.put("priceFormatted", formatAmount(lineItem.getPrice()));
            rows.add(row);
        }
        return rows;
    }

    private static String formatAmount(final BigDecimal amount) {
        return amount == null ? "EUR 0,00" : String.format("EUR %,.2f", amount);
    }

    // ------------------------------------------------------------------
    // Response mapping
    // ------------------------------------------------------------------

    private QuotationResponse toResponse(
            final Quotation quotation, final GuestResponse guest, final Map<UUID, RoomResponse> roomsById) {
        final String guestFullName = guest != null
                ? guest.firstName() + " " + guest.lastName()
                : quotation.getGuestId() != null
                        ? "Unknown Guest"
                        : quotation.getProspectFirstName() + " " + quotation.getProspectLastName();
        final QuotationStatus effectiveStatus = quotation.isExpired() ? QuotationStatus.EXPIRED : quotation.getStatus();
        final String prospectEmail = quotation.getGuestId() == null ? quotation.getProspectEmail() : null;

        final List<QuotationOptionResponse> options = quotation.getOptions().stream()
                .sorted(Comparator.comparingInt(QuotationOption::getPosition))
                .map(option -> new QuotationOptionResponse(
                        option.getId(),
                        option.getLabel(),
                        option.getPosition(),
                        option.getTotalPrice(),
                        toLineItemResponses(option.getLineItems(), roomsById)))
                .toList();

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
                options,
                quotation.getAcceptedOptionId(),
                quotation.isSendFailed(),
                quotation.getSendFailureReason(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt());
    }

    private List<QuotationLineItemResponse> toLineItemResponses(
            final List<QuotationLineItem> lineItems, final Map<UUID, RoomResponse> roomsById) {
        return lineItems.stream()
                .map(lineItem -> {
                    final RoomResponse room = roomsById.get(lineItem.getRoomId());
                    return new QuotationLineItemResponse(
                            lineItem.getId(), lineItem.getRoomId(), room.roomNumber(), room.roomType().name(),
                            lineItem.getPrice());
                })
                .toList();
    }

    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }

    /**
     * A priced set of line items and their combined total — the shared result
     * of {@link #resolveLineItems}.
     *
     * @param lineItems the priced, not-yet-persisted line items
     * @param total     the sum of every line item's price
     */
    private record ResolvedLineItems(List<QuotationLineItem> lineItems, BigDecimal total) {
    }

    /**
     * One priced option, not yet attached to a {@link Quotation} — the result
     * of {@link #resolveOptions} before {@link #applyOptions} turns each entry
     * into a persisted {@link QuotationOption}.
     *
     * @param label     the option's display label
     * @param lineItems the option's priced, not-yet-persisted line items
     * @param total     the sum of {@code lineItems}' prices
     */
    private record ResolvedOption(String label, List<QuotationLineItem> lineItems, BigDecimal total) {
    }
}
