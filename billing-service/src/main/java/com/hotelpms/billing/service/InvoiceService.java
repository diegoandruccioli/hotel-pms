package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
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
}
