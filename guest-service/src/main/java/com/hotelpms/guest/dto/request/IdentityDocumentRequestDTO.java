package com.hotelpms.guest.dto.request;

import com.hotelpms.guest.model.enums.DocumentType;
import com.hotelpms.guest.util.ValidationConstants;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
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
        @NotBlank
        @Size(max = ValidationConstants.MAX_DOCUMENT_NUMBER_LENGTH)
        @Pattern(regexp = ValidationConstants.DOCUMENT_NUMBER_PATTERN)
        String documentNumber,
        @NotNull @Past LocalDate issueDate,
        @NotNull @FutureOrPresent LocalDate expiryDate,
        @Size(max = ValidationConstants.MAX_COUNTRY_LENGTH)
        @Pattern(regexp = ValidationConstants.LOCATION_PATTERN)
        String issuingCountry) {
}
