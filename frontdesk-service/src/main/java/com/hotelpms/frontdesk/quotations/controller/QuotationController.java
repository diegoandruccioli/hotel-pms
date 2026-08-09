package com.hotelpms.frontdesk.quotations.controller;

import com.hotelpms.frontdesk.quotations.dto.QuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationResponse;
import com.hotelpms.frontdesk.quotations.service.QuotationService;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for quotations (preventivi). Reachable by every operational role
 * (RECEPTIONIST/ADMIN/OWNER) — creating and sending a quotation is
 * operationally equivalent to creating a reservation, which carries the same
 * access level.
 */
@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private static final String PDF_FILENAME_PREFIX = "preventivo-";
    private static final String PDF_EXTENSION = ".pdf";

    private final QuotationService quotationService;

    /**
     * Creates a quotation.
     *
     * @param request the quotation details
     * @return the created quotation
     */
    @PostMapping
    public ResponseEntity<QuotationResponse> createQuotation(
            @NonNull @Valid @RequestBody final QuotationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quotationService.createQuotation(request));
    }

    /**
     * Lists every active quotation for the caller's hotel, newest first.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of quotations
     */
    @GetMapping
    public ResponseEntity<Page<QuotationResponse>> getAllQuotations(final Pageable pageable) {
        return ResponseEntity.ok(quotationService.getAllQuotations(pageable));
    }

    /**
     * Gets a quotation by id.
     *
     * @param id the quotation UUID
     * @return the quotation
     */
    @GetMapping("/{id}")
    public ResponseEntity<QuotationResponse> getQuotationById(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(quotationService.getQuotationById(id));
    }

    /**
     * Downloads the quotation PDF.
     *
     * @param id the quotation UUID
     * @return the PDF bytes with {@code Content-Disposition: attachment}
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getQuotationPdf(@NonNull @PathVariable final UUID id) {
        final byte[] pdf = quotationService.getQuotationPdf(id);
        final ContentDisposition disposition = ContentDisposition.attachment()
                .filename(PDF_FILENAME_PREFIX + id + PDF_EXTENSION)
                .build();
        return ResponseEntity.ok()
                .headers(h -> h.setContentDisposition(disposition))
                .body(pdf);
    }

    /**
     * Emails the quotation (with the PDF attached) to the guest or prospect.
     *
     * @param id the quotation UUID
     * @return the updated quotation
     */
    @PostMapping("/{id}/send")
    public ResponseEntity<QuotationResponse> sendQuotation(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(quotationService.sendQuotationEmail(id));
    }

    /**
     * Converts an accepted quotation into a reservation, honoring the frozen
     * quoted price.
     *
     * @param id the quotation UUID
     * @return the created reservation
     */
    @PostMapping("/{id}/convert")
    public ResponseEntity<ReservationResponse> convertToReservation(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(quotationService.convertToReservation(id));
    }

    /**
     * Marks a quotation as declined.
     *
     * @param id the quotation UUID
     * @return the updated quotation
     */
    @PostMapping("/{id}/decline")
    public ResponseEntity<QuotationResponse> declineQuotation(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(quotationService.declineQuotation(id));
    }

    /**
     * Soft-deletes a quotation.
     *
     * @param id the quotation UUID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuotation(@NonNull @PathVariable final UUID id) {
        quotationService.deleteQuotation(id);
    }
}
