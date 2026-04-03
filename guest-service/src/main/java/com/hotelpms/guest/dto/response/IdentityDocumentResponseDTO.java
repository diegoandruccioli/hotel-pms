package com.hotelpms.guest.dto.response;

import com.hotelpms.guest.model.enums.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for identity document responses.
 *
 * @param id             The unique ID of the document.
 * @param documentType   The document type.
 * @param documentNumber The document number.
 * @param issueDate      The date of issue.
 * @param expiryDate     The date of expiry.
 * @param issuingCountry The country of issue.
 * @param createdAt      The creation timestamp.
 * @param updatedAt      The last update timestamp.
 */
public record IdentityDocumentResponseDTO(
        UUID id,
        DocumentType documentType,
        String documentNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String issuingCountry,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
