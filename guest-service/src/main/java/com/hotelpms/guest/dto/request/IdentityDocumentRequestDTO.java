package com.hotelpms.guest.dto.request;

import com.hotelpms.guest.model.enums.DocumentType;
import com.hotelpms.guest.util.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO for identity document creation/update requests.
 *
 * @param documentType   The document type.
 * @param documentNumber The document number.
 * @param issueDate      The issue date.
 * @param expiryDate     The expiry date.
 * @param issuingCountry The issuing country.
 */
public record IdentityDocumentRequestDTO(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = ValidationConstants.MAX_DOCUMENT_NUMBER_LENGTH) String documentNumber,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate expiryDate,
        @Size(max = ValidationConstants.MAX_COUNTRY_LENGTH) String issuingCountry) {
}
