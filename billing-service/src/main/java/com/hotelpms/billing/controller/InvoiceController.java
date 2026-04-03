package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.lang.NonNull;
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
}
