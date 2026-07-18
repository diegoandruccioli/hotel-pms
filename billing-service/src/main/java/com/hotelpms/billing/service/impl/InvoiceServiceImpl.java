package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.domain.InvoiceSequence;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.GuestInvoiceCheckResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.InvoiceSearchResultResponse;
import com.hotelpms.billing.dto.InvoiceSummaryResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceChargeMapper;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.InvoiceSequenceRepository;
import com.hotelpms.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the InvoiceService interface for billing processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final String INVOICE_NOT_FOUND = "INVOICE_NOT_FOUND";
    private static final int GUEST_SEARCH_MATCH_CAP = 200;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceChargeRepository invoiceChargeRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceChargeMapper invoiceChargeMapper;
    private final GuestClient guestClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse createInvoiceForStay(@NonNull final StayInvoiceRequest request) {
        log.info("Creating invoice for stay {}", request.stayId());
        final UUID hotelId = resolveHotelId();

        invoiceRepository.findByStayIdAndHotelId(request.stayId(), hotelId)
                .filter(existing -> existing.getStatus() == InvoiceStatus.ISSUED)
                .ifPresent(existing -> {
                    throw new InvoiceConflictException("INVOICE_ALREADY_EXISTS_FOR_STAY");
                });

        final Invoice invoice = Invoice.builder()
                .stayId(request.stayId())
                .guestId(request.guestId())
                .reservationId(request.reservationId())
                .hotelId(hotelId)
                .totalAmount(BigDecimal.ZERO)
                .status(InvoiceStatus.ISSUED)
                .issueDate(LocalDateTime.now())
                .invoiceNumber(generateInvoiceNumber(hotelId))
                .build();

        final Invoice savedInvoice = invoiceRepository.save(Objects.requireNonNull(invoice));
        log.info("Created invoice {} for stay {}", savedInvoice.getInvoiceNumber(), request.stayId());

        return invoiceMapper.toResponse(savedInvoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ChargeResponse addCharge(@NonNull final UUID stayId, @NonNull final ChargeRequest request) {
        log.info("Adding charge type={} amount={} to stay {}", request.type(), request.amount(), stayId);
        final UUID hotelId = resolveHotelId();

        final Invoice invoice = invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND_FOR_STAY"));

        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvoiceConflictException("INVOICE_NOT_OPEN");
        }

        final InvoiceCharge charge = InvoiceCharge.builder()
                .type(request.type())
                .description(request.description())
                .amount(request.amount())
                .vatRate(vatRateFor(request.type()))
                .referenceId(request.referenceId())
                .build();

        invoice.addCharge(charge);
        final InvoiceCharge savedCharge = invoiceChargeRepository.save(Objects.requireNonNull(charge));

        invoice.setTotalAmount(invoice.getTotalAmount().add(request.amount()));
        invoiceRepository.save(Objects.requireNonNull(invoice));

        log.info("Added {} charge of {} to invoice {} (new total: {})",
                request.type(), request.amount(), invoice.getInvoiceNumber(), invoice.getTotalAmount());

        return invoiceChargeMapper.toResponse(savedCharge);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(@NonNull final UUID id) {
        log.info("Fetching invoice with id {}", id);
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        return invoiceMapper.toResponse(invoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getLatestInvoiceByReservation(@NonNull final UUID reservationId) {
        log.info("Fetching latest invoice for reservation {}", reservationId);
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository
                .findFirstByReservationIdAndHotelIdOrderByIssueDateDesc(reservationId, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        return invoiceMapper.toResponse(invoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getAllInvoices(final Pageable pageable) {
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final UUID hotelId = resolveHotelId();
        return invoiceRepository.findByHotelId(hotelId, safePageable)
                .map(invoiceMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceSearchResultResponse> searchInvoices(
            final InvoiceStatus status, final String query, final LocalDate dateFrom,
            final LocalDate dateTo, final Pageable pageable) {
        final UUID hotelId = resolveHotelId();
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final String trimmedQuery = query == null || query.isBlank() ? null : query.trim();
        final List<UUID> guestIds = trimmedQuery == null ? List.of() : resolveGuestIds(trimmedQuery);

        final Page<Invoice> results = invoiceRepository.searchInvoicesByHotelId(
                hotelId,
                status,
                dateFrom == null ? null : dateFrom.atStartOfDay(),
                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(),
                trimmedQuery,
                guestIds,
                safePageable);

        final Map<UUID, String> guestNames = resolveGuestNames(
                results.getContent().stream().map(Invoice::getGuestId).distinct().toList());

        return results.map(invoice -> new InvoiceSearchResultResponse(
                invoiceMapper.toResponse(invoice), guestNames.get(invoice.getGuestId())));
    }

    /**
     * Resolves which guest IDs (within the caller's hotel) match a free-text query,
     * via a cross-service call to guest-service (Invoice only stores a guestId, not
     * a name/email). Capped at {@link #GUEST_SEARCH_MATCH_CAP} matches — invoice
     * search is a filter aid, not a guest directory export.
     *
     * @param query the free-text query (already trimmed, non-blank)
     * @return matching guest IDs, or an empty list if guest-service is unavailable
     *         (circuit breaker fallback) or nothing matched
     */
    private List<UUID> resolveGuestIds(final String query) {
        return guestClient.searchGuests(query, GUEST_SEARCH_MATCH_CAP).content().stream()
                .map(GuestResponse::id)
                .toList();
    }

    /**
     * Batch-resolves display names for a set of guest IDs in a single round-trip,
     * used to populate {@code InvoiceResponse.guestName} for a page of search
     * results without one Feign call per row.
     *
     * @param guestIds the guest IDs to resolve (may be empty)
     * @return a map of guestId to "First Last", missing entries omitted rather than
     *         failing the whole search (guest-service unavailable or guest deleted)
     */
    private Map<UUID, String> resolveGuestNames(final List<UUID> guestIds) {
        if (guestIds.isEmpty()) {
            return Map.of();
        }
        return guestClient.getGuestsBatch(guestIds).stream()
                .collect(Collectors.toMap(GuestResponse::id,
                        g -> (g.firstName() + " " + g.lastName()).trim(),
                        (first, second) -> first));
    }

    /**
     * Extracts the hotel UUID from the current authentication context.
     * The hotel ID is stored as {@code details} by
     * {@link com.hotelpms.billing.security.InternalAuthFilter} after reading the
     * {@code X-Auth-Hotel} header injected by the API Gateway.
     *
     * @return the hotel UUID of the authenticated caller
     * @throws IllegalStateException if the security context is missing or malformed
     */
    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr)) {
            throw new IllegalStateException("MISSING_HOTEL_CONTEXT");
        }
        return UUID.fromString(hotelIdStr);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse updateDocumentType(@NonNull final UUID invoiceId,
                                               @NonNull final DocumentType documentType) {
        log.info("Updating document type for invoice {} to {}", invoiceId, documentType);
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceConflictException("CANNOT_UPDATE_CANCELLED_INVOICE");
        }
        invoice.setDocumentType(documentType);
        final Invoice saved = invoiceRepository.save(Objects.requireNonNull(invoice));
        return invoiceMapper.toResponse(Objects.requireNonNull(saved));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse updateSdiStatus(@NonNull final UUID invoiceId,
                                            @NonNull final SdiStatus sdiStatus) {
        log.info("Updating SDI status for invoice {} to {}", invoiceId, sdiStatus);
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceConflictException("CANNOT_UPDATE_CANCELLED_INVOICE");
        }
        if (invoice.getDocumentType() != DocumentType.FATTURA) {
            throw new InvoiceConflictException("SDI_ONLY_VALID_FOR_FATTURA");
        }
        invoice.setSdiStatus(sdiStatus);
        final Invoice saved = invoiceRepository.save(Objects.requireNonNull(invoice));
        return invoiceMapper.toResponse(Objects.requireNonNull(saved));
    }

    private static BigDecimal vatRateFor(final ChargeType type) {
        return switch (type) {
            case ROOM_NIGHT, FB_ORDER -> new BigDecimal("0.10");
            case EXTRA -> new BigDecimal("0.22");
        };
    }

    /**
     * Genera il numero fattura progressivo per anno solare nel formato {@code YYYY/NNNN}.
     * Acquisisce un lock pessimistico sulla riga (hotelId, year) per garantire
     * unicità e assenza di gap anche sotto carico concorrente.
     * Deve essere invocato nell'ambito di un contesto {@code @Transactional} attivo.
     *
     * @param hotelId hotel tenant
     * @return numero fattura nel formato {@code 2026/0001}
     */
    private String generateInvoiceNumber(final UUID hotelId) {
        final int year = LocalDate.now().getYear();
        final InvoiceSequence seq = sequenceRepository
                .findByHotelIdAndYearForUpdate(hotelId, year)
                .orElseGet(() -> InvoiceSequence.startFor(hotelId, year));
        seq.setLastSeq(seq.getLastSeq() + 1);
        sequenceRepository.save(seq);
        return String.format("%d/%04d", year, seq.getLastSeq());
    }

    @Override
    @Transactional(readOnly = true)
    public final GuestInvoiceCheckResponse getLastInvoiceDateForGuest(
            @NonNull final UUID guestId, @NonNull final UUID hotelId) {
        final Optional<Invoice> latest = invoiceRepository
                .findTopByGuestIdAndHotelIdOrderByIssueDateDesc(guestId, hotelId);
        if (latest.isEmpty() || latest.get().getIssueDate() == null) {
            return new GuestInvoiceCheckResponse(false, null);
        }
        return new GuestInvoiceCheckResponse(true,
                latest.get().getIssueDate().toLocalDate());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> getInvoiceHistoryForGuest(
            @NonNull final UUID guestId, @NonNull final UUID hotelId) {
        return invoiceRepository
                .findByGuestIdAndHotelIdOrderByIssueDateDesc(guestId, hotelId)
                .stream()
                .map(inv -> new InvoiceSummaryResponse(
                        inv.getId(),
                        inv.getInvoiceNumber(),
                        inv.getIssueDate(),
                        inv.getTotalAmount(),
                        inv.getStatus()))
                .toList();
    }
}
