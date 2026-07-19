package com.hotelpms.guest.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for guest read operations.
 *
 * @param id                The unique ID of the guest.
 * @param firstName         The first name of the guest.
 * @param lastName          The last name of the guest.
 * @param email             The email address of the guest.
 * @param phone             The phone number of the guest.
 * @param address           The street address of the guest.
 * @param city              The city of the guest.
 * @param country           The country of the guest.
 * @param dateOfBirth       The date of birth of the guest.
 * @param fiscalCode        Italian Codice Fiscale (optional).
 * @param vatNumber         Partita IVA / VAT number (optional).
 * @param companyName       Company / legal entity name (optional).
 * @param sdiCode           SDI/Destinatario code (optional).
 * @param pecEmail          PEC email (optional).
 * @param cap               CAP — Italian 5-digit postal code (optional).
 * @param comune            Comune — municipality name (optional).
 * @param provincia         Provincia — 2-letter province code (optional).
 * @param identityDocuments The list of identity documents belonging to the
 *                          guest.
 * @param gdprConsentDate   The date GDPR consent was recorded.
 * @param createdAt         The creation timestamp.
 * @param updatedAt         The last update timestamp.
 */
@SuppressWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record GuestResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String country,
        LocalDate dateOfBirth,
        String fiscalCode,
        String vatNumber,
        String companyName,
        String sdiCode,
        String pecEmail,
        String cap,
        String comune,
        String provincia,
        List<IdentityDocumentResponseDTO> identityDocuments,
        LocalDate gdprConsentDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Compact constructor — defensively copies the mutable list on construction.
     */
    public GuestResponse {
        if (identityDocuments != null) {
            identityDocuments = List.copyOf(identityDocuments);
        }
    }

    /**
     * Returns a defensive copy of the identity documents list.
     *
     * @return an unmodifiable copy of the identity documents list, or {@code null}
     *         if no documents are present
     */
    @Override
    public List<IdentityDocumentResponseDTO> identityDocuments() {
        return identityDocuments == null ? null : List.copyOf(identityDocuments);
    }
}
