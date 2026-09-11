package com.hotelpms.billing.service.impl;

import com.hotelpms.internalauth.security.TenantContext;

import com.hotelpms.commonweb.csv.CsvWriter;
import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.FolioType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.domain.InvoiceSequence;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.GroupChargeRequest;
import com.hotelpms.billing.dto.GuestInvoiceCheckResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.InvoiceSearchResultResponse;
import com.hotelpms.billing.dto.InvoiceSummaryResponse;
import com.hotelpms.billing.dto.MasterFolioRequest;
import com.hotelpms.billing.dto.StayInvoiceCheckResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceChargeMapper;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.InvoiceFiscalExportRepository;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.InvoiceSequenceRepository;
import com.hotelpms.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the InvoiceService interface for billing processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final String INVOICE_NOT_FOUND = "INVOICE_NOT_FOUND";
    private static final String INVOICE_NOT_OPEN = "INVOICE_NOT_OPEN";
    private static final int GUEST_SEARCH_MATCH_CAP = 200;
    /**
     * Page size for CSV export's internal pagination loop -- bounds memory to one
     * page at a time instead of loading the whole matching set before writing.
     */
    private static final int EXPORT_PAGE_SIZE = 500;
    /**
     * The FatturaPA {@code Natura} code for the tourist tax — collected by the
     * operator in the comune's name, not as consideration for a service of its own
     * (art. 15 c.1 n.3 D.P.R. 633/1972, "anticipazioni fatte in nome e per conto").
     * Kept as a single named constant, not inlined, so a future correction (e.g. to
     * {@code N2.2}) after commercialista review is a one-line change.
     */
    private static final String NATURA_CITY_TAX = "N1";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceChargeRepository invoiceChargeRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final InvoiceFiscalExportRepository invoiceFiscalExportRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceChargeMapper invoiceChargeMapper;
    private final GuestClient guestClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse createInvoiceForStay(@NonNull final StayInvoiceRequest request) {
        log.info("Creating invoice for stay {}", request.stayId());
        final UUID hotelId = TenantContext.resolveHotelId();

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
    public InvoiceResponse createMasterFolioForGroup(
            @NonNull final UUID groupId, @NonNull final MasterFolioRequest request) {
        final UUID hotelId = TenantContext.resolveHotelId();
        log.info("Opening master folio for group {} | hotelId={}", groupId, hotelId);

        final Optional<Invoice> existing = invoiceRepository
                .findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER)
                .filter(inv -> inv.getStatus() == InvoiceStatus.ISSUED);
        if (existing.isPresent()) {
            return invoiceMapper.toResponse(existing.get());
        }

        final Invoice invoice = Invoice.builder()
                .groupId(groupId)
                .folioType(FolioType.MASTER)
                .guestId(request.guestId())
                .hotelId(hotelId)
                .totalAmount(BigDecimal.ZERO)
                .status(InvoiceStatus.ISSUED)
                .issueDate(LocalDateTime.now())
                .invoiceNumber(generateInvoiceNumber(hotelId))
                .build();

        final Invoice savedInvoice = invoiceRepository.save(Objects.requireNonNull(invoice));
        log.info("Created master folio {} for group {}", savedInvoice.getInvoiceNumber(), groupId);

        return invoiceMapper.toResponse(savedInvoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ChargeResponse addChargeToGroupFolio(
            @NonNull final UUID groupId, @NonNull final GroupChargeRequest request) {
        log.info("Adding charge type={} amount={} to master folio of group {}",
                request.type(), request.amount(), groupId);
        final UUID hotelId = TenantContext.resolveHotelId();

        final Invoice invoice = invoiceRepository
                .findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER)
                .orElseThrow(() -> new NotFoundException("MASTER_FOLIO_NOT_FOUND_FOR_GROUP"));

        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvoiceConflictException(INVOICE_NOT_OPEN);
        }
        assertNotFiscallyLocked(invoice);

        final VatTreatment vatTreatment = vatTreatmentFor(request.type());
        final InvoiceCharge charge = InvoiceCharge.builder()
                .type(request.type())
                .description(request.description())
                .amount(request.amount())
                .vatRate(vatTreatment.rate())
                .naturaCode(vatTreatment.naturaCode())
                .unitPrice(request.unitPrice())
                .nights(request.nights())
                .routedFromStayId(request.routedFromStayId())
                .build();

        invoice.addCharge(charge);
        final InvoiceCharge savedCharge = invoiceChargeRepository.save(Objects.requireNonNull(charge));

        invoice.setTotalAmount(invoice.getTotalAmount().add(request.amount()));
        invoiceRepository.save(Objects.requireNonNull(invoice));

        log.info("Added {} charge of {} to master folio {} (new total: {})",
                request.type(), request.amount(), invoice.getInvoiceNumber(), invoice.getTotalAmount());

        return invoiceChargeMapper.toResponse(savedCharge);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ChargeResponse addCharge(@NonNull final UUID stayId, @NonNull final ChargeRequest request) {
        log.info("Adding charge type={} amount={} to stay {}", request.type(), request.amount(), stayId);
        final UUID hotelId = TenantContext.resolveHotelId();

        final Invoice invoice = invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND_FOR_STAY"));

        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvoiceConflictException(INVOICE_NOT_OPEN);
        }
        assertNotFiscallyLocked(invoice);

        final VatTreatment vatTreatment = vatTreatmentFor(request.type());
        final InvoiceCharge charge = InvoiceCharge.builder()
                .type(request.type())
                .description(request.description())
                .amount(request.amount())
                .vatRate(vatTreatment.rate())
                .naturaCode(vatTreatment.naturaCode())
                .referenceId(request.referenceId())
                .unitPrice(request.unitPrice())
                .nights(request.nights())
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
    @Transactional
    public void removeCharge(@NonNull final UUID stayId, @NonNull final UUID chargeId) {
        log.info("Removing charge {} from stay {}", chargeId, stayId);
        final UUID hotelId = TenantContext.resolveHotelId();

        final Invoice invoice = invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND_FOR_STAY"));

        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvoiceConflictException(INVOICE_NOT_OPEN);
        }
        assertNotFiscallyLocked(invoice);

        // Scoped to this invoice, not a bare findById — a charge id that belongs to a
        // different invoice (wrong stay, wrong hotel) must 404 exactly like it never
        // existed, never leak whether it exists elsewhere.
        final InvoiceCharge charge = invoiceChargeRepository.findById(chargeId)
                .filter(c -> c.getInvoice().getId().equals(invoice.getId()))
                .orElseThrow(() -> new NotFoundException("CHARGE_NOT_FOUND"));

        invoice.removeCharge(charge);
        invoiceChargeRepository.delete(charge);

        invoice.setTotalAmount(invoice.getTotalAmount().subtract(charge.getAmount()));
        invoiceRepository.save(Objects.requireNonNull(invoice));

        log.info("Removed {} charge of {} from invoice {} (new total: {})",
                charge.getType(), charge.getAmount(), invoice.getInvoiceNumber(), invoice.getTotalAmount());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(@NonNull final UUID id) {
        log.info("Fetching invoice with id {}", id);
        final UUID hotelId = TenantContext.resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        return invoiceMapper.toResponse(invoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getLatestInvoiceByReservation(@NonNull final UUID reservationId) {
        log.info("Fetching latest invoice for reservation {}", reservationId);
        final UUID hotelId = TenantContext.resolveHotelId();
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
        final UUID hotelId = TenantContext.resolveHotelId();
        return invoiceRepository.findByHotelId(hotelId, safePageable)
                .map(invoiceMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceSearchResultResponse> searchInvoices(
            final InvoiceStatus status, final String query, final LocalDate dateFrom,
            final LocalDate dateTo, final Pageable pageable) {
        final UUID hotelId = TenantContext.resolveHotelId();
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
                results.getContent().stream()
                        .map((@NonNull Invoice invoice) -> invoice.getGuestId())
                        .distinct()
                        .toList());

        return results.map(invoice -> new InvoiceSearchResultResponse(
                invoiceMapper.toResponse(invoice), guestNames.get(invoice.getGuestId())));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public void exportInvoicesCsv(final InvoiceStatus status, final String query, final LocalDate dateFrom,
            final LocalDate dateTo, final OutputStream out) throws IOException {
        final UUID hotelId = TenantContext.resolveHotelId();
        log.info("REST request to export invoices CSV | hotelId={} | status={} | hasQuery={}",
                hotelId, status, query != null && !query.isBlank());
        final String trimmedQuery = query == null || query.isBlank() ? null : query.trim();
        final List<UUID> guestIds = trimmedQuery == null ? List.of() : resolveGuestIds(trimmedQuery);
        final LocalDateTime fromInclusive = dateFrom == null ? null : dateFrom.atStartOfDay();
        final LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        try (CsvWriter csv = CsvWriter.open(out, List.of(
                "invoiceNumber", "guestName", "issueDate", "status", "documentType", "totalAmount"))) {
            int pageNumber = 0;
            Page<Invoice> page;
            do {
                final Pageable pageable = PageRequest.of(
                        pageNumber, EXPORT_PAGE_SIZE, Sort.by("issueDate").descending());
                page = invoiceRepository.searchInvoicesByHotelId(
                        hotelId, status, fromInclusive, toExclusive, trimmedQuery, guestIds, pageable);

                final Map<UUID, String> guestNames = resolveGuestNames(
                        page.getContent().stream()
                                .map((@NonNull Invoice invoice) -> invoice.getGuestId())
                                .distinct()
                                .toList());

                for (final Invoice invoice : page.getContent()) {
                    csv.printRow(List.of(
                            invoice.getInvoiceNumber(),
                            guestNames.getOrDefault(invoice.getGuestId(), ""),
                            String.valueOf(invoice.getIssueDate()),
                            String.valueOf(invoice.getStatus()),
                            String.valueOf(invoice.getDocumentType()),
                            String.valueOf(invoice.getTotalAmount())));
                }
                pageNumber++;
            } while (page.hasNext());
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesInPeriod(@NonNull final LocalDate from, @NonNull final LocalDate to) {
        final UUID hotelId = TenantContext.resolveHotelId();
        return invoiceRepository
                .findByHotelIdAndIssueDateBetween(hotelId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .sorted(Comparator.comparing(Invoice::getIssueDate))
                .map(invoiceMapper::toResponse)
                .toList();
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
                .map((@NonNull GuestResponse gr) -> gr.id())
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
                .collect(Collectors.toMap((@NonNull GuestResponse gr) -> gr.id(),
                        g -> (g.firstName() + " " + g.lastName()).trim(),
                        (first, second) -> first));
    }

    /**
     * Blocks mutation of fiscally-relevant invoice state once at least one FatturaPA
     * export has been generated for it (see {@code InvoiceFiscalExport}). An Italian
     * fiscal invoice is corrected via nota di credito, not by editing the original —
     * this guard exists so the gap doesn't widen further while that flow is built
     * separately; it does not itself implement corrections.
     *
     * @param invoice the invoice about to be mutated
     * @throws InvoiceConflictException if the invoice has already been fiscally exported
     */
    private void assertNotFiscallyLocked(final Invoice invoice) {
        if (invoiceFiscalExportRepository.existsByInvoiceId(invoice.getId())) {
            throw new InvoiceConflictException("INVOICE_LOCKED_AFTER_EXPORT");
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse updateDocumentType(@NonNull final UUID invoiceId,
                                               @NonNull final DocumentType documentType) {
        log.info("Updating document type for invoice {} to {}", invoiceId, documentType);
        final UUID hotelId = TenantContext.resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceConflictException("CANNOT_UPDATE_CANCELLED_INVOICE");
        }
        // NOT a blanket PAID guard (removed — was CANNOT_UPDATE_PAID_INVOICE): a paid-but-
        // never-exported invoice legitimately switches RICEVUTA->FATTURA all the time (guest
        // pays, then asks for a proper fattura) — that's the common case, not an edge case.
        // assertNotFiscallyLocked below is the guard that actually matters: once a FatturaPA
        // has been generated for this invoice, its type must not change underneath the
        // already-transmitted snapshot.
        assertNotFiscallyLocked(invoice);
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
        final UUID hotelId = TenantContext.resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)
                .orElseThrow(() -> new NotFoundException(INVOICE_NOT_FOUND));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceConflictException("CANNOT_UPDATE_CANCELLED_INVOICE");
        }
        if (invoice.getDocumentType() != DocumentType.FATTURA) {
            throw new InvoiceConflictException("SDI_ONLY_VALID_FOR_FATTURA");
        }
        // Deliberately NOT behind assertNotFiscallyLocked: sdiStatus is transmission
        // bookkeeping (NOT_SENT/SENT/ACCEPTED/REJECTED), never read by
        // FatturaPAServiceImpl when building the XML — recording it is the operator's
        // natural *next* step right after generateXml() locks the invoice, so blocking
        // it here would make the field permanently stuck at its default forever.
        invoice.setSdiStatus(sdiStatus);
        final Invoice saved = invoiceRepository.save(Objects.requireNonNull(invoice));
        return invoiceMapper.toResponse(Objects.requireNonNull(saved));
    }

    private static VatTreatment vatTreatmentFor(final ChargeType type) {
        return switch (type) {
            case ROOM_NIGHT, FB_ORDER -> new VatTreatment(new BigDecimal("0.10"), null);
            case EXTRA -> new VatTreatment(new BigDecimal("0.22"), null);
            case CITY_TAX -> new VatTreatment(BigDecimal.ZERO, NATURA_CITY_TAX);
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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public GuestInvoiceCheckResponse getLastInvoiceDateForGuest(
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
    public StayInvoiceCheckResponse getLastInvoiceDateForStay(
            @NonNull final UUID stayId, @NonNull final UUID hotelId) {
        final Optional<LocalDate> ownFolioDate = invoiceRepository
                .findTopByStayIdAndHotelIdOrderByIssueDateDesc(stayId, hotelId)
                .map(Invoice::getIssueDate)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate);

        // A stay's own folio can close at zero once its ROOM_NIGHT/CITY_TAX charges
        // move to a reservation group's MASTER folio at check-out (Punto 4) — the
        // fiscal retention clock must follow whichever invoice actually carries
        // the money, not just the stay's own (possibly empty) folio.
        final Optional<LocalDate> routedFolioDate = invoiceRepository
                .findByRoutedFromStayIdAndHotelId(stayId, hotelId)
                .stream()
                .map(Invoice::getIssueDate)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo);

        final Optional<LocalDate> mostRecent = Stream.of(ownFolioDate, routedFolioDate)
                .flatMap(Optional::stream)
                .max(LocalDate::compareTo);

        return mostRecent
                .map(date -> new StayInvoiceCheckResponse(true, date))
                .orElseGet(() -> new StayInvoiceCheckResponse(false, null));
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

    /**
     * The VAT rate and, when the charge is out of VAT scope, the FatturaPA
     * {@code Natura} code to record alongside it. Derived only from the server-known
     * {@link ChargeType} — never accepted from the client, same non-forgeable
     * property {@code vatRate} alone already had.
     *
     * @param rate       the VAT rate
     * @param naturaCode the FatturaPA {@code Natura} code, or {@code null} for an
     *                   ordinary taxable charge
     */
    private record VatTreatment(BigDecimal rate, String naturaCode) {
    }
}
