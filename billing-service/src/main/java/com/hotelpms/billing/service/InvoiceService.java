package com.hotelpms.billing.service;

import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.GroupChargeRequest;
import com.hotelpms.billing.dto.GuestInvoiceCheckResponse;
import com.hotelpms.billing.dto.StayInvoiceCheckResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.InvoiceSearchResultResponse;
import com.hotelpms.billing.dto.InvoiceSummaryResponse;
import com.hotelpms.billing.dto.MasterFolioRequest;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing Invoices.
 */
public interface InvoiceService {

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
     * Removes a charge from the open invoice for a stay — the reversal counterpart of
     * {@link #addCharge}, for a charge that turns out to no longer apply (e.g. a guest
     * removed from a stay after their tourist-tax charge was already posted). Updates
     * {@code Invoice.totalAmount} atomically. Unlike a discount or credit note, this
     * deletes the line entirely: it corrects a charge that should never have stood,
     * not one being waived after the fact.
     *
     * <p>Returns 404 if no ISSUED invoice exists for the stay in the caller's hotel, or
     * if {@code chargeId} does not belong to that invoice (IDOR-safe). Returns 409 if
     * the invoice is not in ISSUED status, or is fiscally locked after export — same
     * guards as {@link #addCharge}, since an invoice that can no longer receive new
     * charges must equally not have existing ones removed.
     *
     * @param stayId   the stay UUID used to look up the invoice
     * @param chargeId the charge UUID to remove
     */
    void removeCharge(@NonNull UUID stayId, @NonNull UUID chargeId);

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
     * Combinable search over the caller's hotel invoices (C12): optional status,
     * optional issue-date window, and an optional free-text query matched against
     * the invoice number or the associated guest's name/email (resolved via a
     * cross-service call to guest-service, since Invoice only stores a guestId).
     * Every filter left {@code null} is skipped entirely, not treated as "no match".
     * Results include {@code guestName}, batch-resolved for the returned page only.
     *
     * @param status   optional invoice status filter, or {@code null}
     * @param query    optional free-text query (invoice number or guest name/email),
     *                 or {@code null}/blank to skip it
     * @param dateFrom optional lower bound on issue date (inclusive day), or {@code null}
     * @param dateTo   optional upper bound on issue date (inclusive day), or {@code null}
     * @param pageable pagination parameters
     * @return a page of matching invoice search results, scoped to the authenticated hotel
     */
    Page<InvoiceSearchResultResponse> searchInvoices(
            InvoiceStatus status, String query, LocalDate dateFrom, LocalDate dateTo, Pageable pageable);

    /**
     * Streams every invoice matching the same filters as {@link
     * #searchInvoices} to {@code out} as CSV, unpaginated internally in
     * fixed-size batches -- financial data, so callers must gate this
     * ADMIN/OWNER.
     *
     * @param status   optional invoice status filter
     * @param query    optional free-text query (invoice number or guest name/email)
     * @param dateFrom optional lower bound on issue date (inclusive day)
     * @param dateTo   optional upper bound on issue date (inclusive day)
     * @param out      the stream to write CSV bytes to
     * @throws java.io.IOException if writing to {@code out} fails
     */
    void exportInvoicesCsv(InvoiceStatus status, String query, LocalDate dateFrom, LocalDate dateTo,
            java.io.OutputStream out) throws java.io.IOException;

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

    /**
     * Returns the most recent invoice date relevant to a stay within a hotel —
     * either the stay's own individual folio, or (if its charges were later
     * transferred to a reservation group's MASTER folio, Punto 4) whichever
     * invoice actually carries them.
     * Used internally by the frontdesk-service GDPR legal-hold guard (E22) to
     * verify whether the Codice Civile art. 2220 ten-year fiscal retention
     * obligation has expired before anonymising a {@code StayGuest} record.
     *
     * @param stayId  the stay UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @return response containing whether a relevant invoice exists and its most recent date
     */
    StayInvoiceCheckResponse getLastInvoiceDateForStay(@NonNull UUID stayId, @NonNull UUID hotelId);

    /**
     * Returns all invoice summaries for a guest within a hotel, ordered by issue
     * date descending. Used by the GDPR Art. 20 data-export endpoint.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @return list of invoice summaries, most recent first
     */
    List<InvoiceSummaryResponse> getInvoiceHistoryForGuest(@NonNull UUID guestId, @NonNull UUID hotelId);

    /**
     * Switches an invoice between fiscal (FATTURA) and non-fiscal (RICEVUTA) type.
     * Rejected for CANCELLED invoices.
     *
     * @param invoiceId    the invoice UUID
     * @param documentType the new document type
     * @return the updated invoice response
     */
    InvoiceResponse updateDocumentType(@NonNull UUID invoiceId, @NonNull DocumentType documentType);

    /**
     * Updates the SDI transmission status of a FATTURA invoice.
     * Only invoices with documentType=FATTURA can have an SDI status.
     * Rejected for CANCELLED invoices and for RICEVUTA document types.
     *
     * @param invoiceId the invoice UUID
     * @param sdiStatus the new SDI status
     * @return the updated invoice response
     */
    InvoiceResponse updateSdiStatus(@NonNull UUID invoiceId, @NonNull SdiStatus sdiStatus);

    /**
     * Returns all invoices for the caller's hotel with an issue date within the given
     * inclusive day range. Used by the FatturaPA batch export to select which invoices
     * to hand off to the commercialista for a given period.
     *
     * @param from inclusive lower bound (day) on issue date
     * @param to   inclusive upper bound (day) on issue date
     * @return matching invoices for the authenticated hotel, ordered by issue date ascending
     */
    List<InvoiceResponse> getInvoicesInPeriod(@NonNull LocalDate from, @NonNull LocalDate to);

    /**
     * Opens a MASTER folio for a reservation group (Punto 4). Returns the existing
     * one if the group already has a master folio in {@code ISSUED} status,
     * instead of creating a duplicate.
     *
     * @param groupId the reservation group's id (frontdesk-service)
     * @param request the master folio request (contact guest)
     * @return the master folio invoice
     */
    InvoiceResponse createMasterFolioForGroup(@NonNull UUID groupId, @NonNull MasterFolioRequest request);

    /**
     * Adds a charge directly to a reservation group's master folio -- the
     * transfer counterpart of {@link #addCharge}, used when a room marked
     * "billed to group" checks out and its ROOM_NIGHT/CITY_TAX charges move off
     * its individual invoice.
     *
     * <p>Returns 404 if no MASTER folio exists for the given group in the
     * caller's hotel. Returns 409 if the master folio is not in {@code ISSUED}
     * status, or is fiscally locked after export -- same guards as {@link
     * #addCharge}.
     *
     * @param groupId the reservation group's id
     * @param request the charge to add, tagging the stay it's transferred from
     * @return the created charge response
     */
    ChargeResponse addChargeToGroupFolio(@NonNull UUID groupId, @NonNull GroupChargeRequest request);
}
