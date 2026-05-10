package com.hotelpms.guest.dto.response;

import com.hotelpms.guest.client.dto.InvoiceSummaryClientResponse;
import com.hotelpms.guest.client.dto.StaySummaryClientResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR Art. 20 data-export response — aggregates all personal data held about
 * a guest across guest-service, stay-service and billing-service.
 *
 * <p>This record is the response to {@code GET /api/v1/guests/{id}/export}.
 * It is intentionally comprehensive: Art. 20 requires the data controller to
 * provide all personal data that the subject has provided in a structured,
 * commonly used, machine-readable format.
 *
 * @param exportedAt        timestamp when the export was generated
 * @param guestId           the guest UUID
 * @param firstName         first name (null if anonymised)
 * @param lastName          last name
 * @param email             email address
 * @param phone             phone number
 * @param address           street address
 * @param city              city
 * @param country           country
 * @param dateOfBirth       date of birth
 * @param gdprConsentDate   date GDPR consent was recorded
 * @param createdAt         profile creation timestamp
 * @param identityDocuments list of attached identity documents
 * @param stays             stay history from stay-service (empty if unavailable)
 * @param invoices          invoice history from billing-service (empty if unavailable)
 */
public record GuestDataExportResponse(
        LocalDateTime exportedAt,
        UUID guestId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String country,
        LocalDate dateOfBirth,
        LocalDate gdprConsentDate,
        LocalDateTime createdAt,
        List<IdentityDocumentResponseDTO> identityDocuments,
        List<StaySummaryClientResponse> stays,
        List<InvoiceSummaryClientResponse> invoices) {

    /** Defensive copy of all mutable lists. */
    public GuestDataExportResponse {
        identityDocuments = identityDocuments == null ? List.of() : List.copyOf(identityDocuments);
        stays = stays == null ? List.of() : List.copyOf(stays);
        invoices = invoices == null ? List.of() : List.copyOf(invoices);
    }
}
