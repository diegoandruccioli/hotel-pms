package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.GuestInvoiceCheckResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.InvoiceSummaryResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.PdfInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller for managing Invoices.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String PDF_FILENAME_PREFIX = "fattura-";
    private static final String PDF_EXTENSION = ".pdf";

    private final InvoiceService invoiceService;
    private final PdfInvoiceService pdfInvoiceService;

    /**
     * Retrieves an invoice by its ID.
     * 
     * @param id the invoice UUID
     * @return the invoice response
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@NonNull @PathVariable final UUID id) {
        log.info("REST request to get invoice {}", id);
        final InvoiceResponse response = invoiceService.getInvoice(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated list of invoices.
     *
     * @param pageable the pagination parameters
     * @return a page of invoice responses
     */
    @GetMapping
    public ResponseEntity<Page<InvoiceResponse>> getAllInvoices(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "issueDate",
                    direction = Sort.Direction.DESC) final Pageable pageable) {
        log.info("REST request to get all invoices");
        final Page<InvoiceResponse> page = invoiceService.getAllInvoices(pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Retrieves the latest invoice for a reservation. Used by the stay-service
     * during check-out to validate billing status.
     *
     * @param reservationId the reservation UUID
     * @return the latest invoice response
     */
    @GetMapping("/reservation/{reservationId}/latest")
    public ResponseEntity<InvoiceResponse> getLatestByReservation(
            @NonNull @PathVariable final UUID reservationId) {
        log.info("REST request to get latest invoice for reservation {}", reservationId);
        final InvoiceResponse response = invoiceService.getLatestInvoiceByReservation(reservationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates an invoice for a hotel stay. Called by stay-service at check-in.
     * Returns 409 if an open invoice already exists for the stay.
     *
     * @param request the stay invoice creation request
     * @return the created invoice response with HTTP 201
     */
    @PostMapping("/stay")
    public ResponseEntity<InvoiceResponse> createInvoiceForStay(
            @NonNull @Valid @RequestBody final StayInvoiceRequest request) {
        log.info("REST request to create invoice for stay {}", request.stayId());
        final InvoiceResponse response = invoiceService.createInvoiceForStay(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Adds a charge to the open invoice for a stay. Called by fb-service after order confirmation.
     * Returns 404 if no open invoice exists for the stay in the caller's hotel.
     * Returns 409 if the invoice is not in ISSUED status.
     *
     * @param stayId  the stay UUID
     * @param request the charge details
     * @return the created charge response with HTTP 201
     */
    @PostMapping("/stay/{stayId}/charges")
    public ResponseEntity<ChargeResponse> addCharge(
            @NonNull @PathVariable final UUID stayId,
            @NonNull @Valid @RequestBody final ChargeRequest request) {
        log.info("REST request to add {} charge to stay {}", request.type(), stayId);
        final ChargeResponse response = invoiceService.addCharge(stayId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the most recent invoice date for a guest within a hotel.
     * Called by guest-service GDPR legal-hold guard (T-GST-05).
     *
     * @param guestId the guest UUID
     * @return response with existence flag and most recent invoice date
     */
    @GetMapping("/guest/{guestId}/last-date")
    public ResponseEntity<GuestInvoiceCheckResponse> getLastInvoiceDateForGuest(
            @NonNull @PathVariable final UUID guestId) {
        final UUID hotelId = extractHotelId();
        log.info("REST request for last invoice date — guest={} hotel={}", guestId, hotelId);
        return ResponseEntity.ok(
                invoiceService.getLastInvoiceDateForGuest(guestId, Objects.requireNonNull(hotelId)));
    }

    /**
     * Returns all invoice summaries for a guest within the caller's hotel.
     * Called by guest-service GDPR Art. 20 data-export endpoint.
     *
     * @param guestId the guest UUID
     * @return list of invoice summaries, most recent first
     */
    @GetMapping("/guest/{guestId}/history")
    public ResponseEntity<List<InvoiceSummaryResponse>> getInvoiceHistoryForGuest(
            @NonNull @PathVariable final UUID guestId) {
        final UUID hotelId = extractHotelId();
        log.info("REST request for invoice history — guest={} hotel={}", guestId, hotelId);
        return ResponseEntity.ok(
                invoiceService.getInvoiceHistoryForGuest(guestId, Objects.requireNonNull(hotelId)));
    }

    /**
     * Returns a PDF representation of the invoice for download.
     *
     * @param id the invoice UUID
     * @return PDF bytes with {@code Content-Disposition: attachment} header
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getInvoicePdf(@NonNull @PathVariable final UUID id) {
        log.info("REST request to download PDF for invoice {}", id);
        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(id);
        final ContentDisposition disposition = ContentDisposition.attachment()
                .filename(PDF_FILENAME_PREFIX + id + PDF_EXTENSION)
                .build();
        return ResponseEntity.ok()
                .headers(h -> h.setContentDisposition(disposition))
                .body(pdf);
    }

    private UUID extractHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
