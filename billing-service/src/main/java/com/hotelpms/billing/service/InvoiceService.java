package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.GuestInvoiceCheckResponse;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.util.UUID;

/**
 * Service interface for managing Invoices.
 */
public interface InvoiceService {

    /**
     * Creates an invoice after validating with reservation and guest services.
     *
     * @param request the invoice creation request
     * @return the created invoice response
     */
    InvoiceResponse createInvoice(@NonNull InvoiceRequest request);

    /**
     * Creates an invoice linked to a hotel stay, called by stay-service at check-in.
     * The invoice is created with totalAmount=0 and status=ISSUED.
     * Returns 409 if an open invoice already exists for the given stay.
     *
     * @param request the stay invoice creation request
     * @return the created invoice response
     */
    InvoiceResponse createInvoiceForStay(@NonNull StayInvoiceRequest request);

    /**
     * Adds a charge to the open invoice for a stay.
     * Updates Invoice.totalAmount atomically.
     * Returns 404 if no ISSUED invoice exists for the stay in the caller's hotel (IDOR-safe).
     * Returns 409 if the invoice is not in ISSUED status.
     *
     * @param stayId  the stay UUID used to look up the invoice
     * @param request the charge details (type, description, amount, referenceId)
     * @return the created charge response
     */
    ChargeResponse addCharge(@NonNull UUID stayId, @NonNull ChargeRequest request);

    /**
     * Retrieves an invoice by its ID.
     *
     * @param id the invoice UUID
     * @return the invoice response
     */
    InvoiceResponse getInvoice(@NonNull UUID id);

    /**
     * Retrieves a paginated list of invoices.
     *
     * @param pageable the pagination parameters
     * @return a page of invoice responses
     */
    Page<InvoiceResponse> getAllInvoices(Pageable pageable);

    /**
     * Retrieves the most recent invoice for a given reservation.
     * Used by the stay-service during check-out to validate billing.
     *
     * @param reservationId the reservation UUID
     * @return the latest invoice response
     */
    InvoiceResponse getLatestInvoiceByReservation(@NonNull UUID reservationId);

    /**
     * Returns the most recent invoice date for a guest within a hotel.
     * Used internally by the guest-service GDPR legal-hold guard (T-GST-05)
     * to verify whether the Codice Civile art. 2220 ten-year fiscal retention
     * obligation has expired before anonymising a guest profile.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @return response containing whether invoices exist and the most recent date
     */
    GuestInvoiceCheckResponse getLastInvoiceDateForGuest(@NonNull UUID guestId, @NonNull UUID hotelId);
}
