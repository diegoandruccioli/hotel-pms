package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final InvoiceService invoiceService;

    /**
     * Creates a new invoice.
     * 
     * @param request the invoice creation request
     * @return the created invoice response
     */
    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@NonNull @Valid @RequestBody final InvoiceRequest request) {
        log.info("REST request to create an invoice");
        final InvoiceResponse response = invoiceService.createInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
}
