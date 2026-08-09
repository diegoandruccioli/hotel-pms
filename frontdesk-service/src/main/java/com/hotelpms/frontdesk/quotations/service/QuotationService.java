package com.hotelpms.frontdesk.quotations.service;

import com.hotelpms.frontdesk.quotations.dto.QuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service for quotations (preventivi): priced stay offers, emailed with a PDF
 * attachment, convertible to a {@code Reservation} at the price they were
 * quoted — even if seasonal rates changed in the meantime.
 */
public interface QuotationService {

    /**
     * Creates a quotation, resolving and freezing the price of every
     * requested room via {@code RatePricingService}.
     *
     * @param request the quotation details
     * @return the created quotation
     */
    QuotationResponse createQuotation(QuotationRequest request);

    /**
     * Retrieves a quotation by id, scoped to the caller's hotel.
     *
     * @param id the quotation UUID
     * @return the quotation response
     */
    QuotationResponse getQuotationById(UUID id);

    /**
     * Lists every active quotation for the caller's hotel, newest first.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of quotation responses
     */
    Page<QuotationResponse> getAllQuotations(Pageable pageable);

    /**
     * Renders the quotation PDF.
     *
     * @param id the quotation UUID
     * @return the PDF bytes
     */
    byte[] getQuotationPdf(UUID id);

    /**
     * Emails the quotation (with the PDF attached) to the guest or prospect.
     * Marks the quotation {@code SENT} on the first successful send; a resend
     * of an already-{@code SENT} quotation is allowed and does not change status.
     *
     * @param id the quotation UUID
     * @return the updated quotation response
     */
    QuotationResponse sendQuotationEmail(UUID id);

    /**
     * Converts an accepted quotation into a {@code Reservation}, honoring the
     * price frozen on each line item regardless of current seasonal rates.
     * If the quotation was made for a prospect (no {@code guestId}), a
     * {@code Guest} is created first. Marks the quotation {@code ACCEPTED}.
     *
     * @param id the quotation UUID
     * @return the created reservation
     */
    ReservationResponse convertToReservation(UUID id);

    /**
     * Marks a quotation as declined.
     *
     * @param id the quotation UUID
     * @return the updated quotation response
     */
    QuotationResponse declineQuotation(UUID id);

    /**
     * Soft-deletes a quotation, scoped to the caller's hotel.
     *
     * @param id the quotation UUID
     */
    void deleteQuotation(UUID id);
}
