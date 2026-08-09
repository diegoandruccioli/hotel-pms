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
     * Replaces a quotation's recipient, stay details and rooms, re-resolving
     * prices via {@code RatePricingService} at current rates. Only permitted
     * while the quotation is still {@code DRAFT} — once sent, a quotation is
     * immutable and {@link #duplicateQuotation} is the way to offer a revised
     * price.
     *
     * @param id      the quotation UUID
     * @param request the replacement quotation details
     * @return the updated quotation
     */
    QuotationResponse updateQuotation(UUID id, QuotationRequest request);

    /**
     * Creates a new {@code DRAFT} quotation with the same recipient, rooms and
     * dates as the source, with prices re-resolved at current rates (it is a
     * new offer, not a copy of the frozen one) and a fresh {@code validUntil}.
     *
     * @param id the source quotation UUID
     * @return the newly created draft
     */
    QuotationResponse duplicateQuotation(UUID id);

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
