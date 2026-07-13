package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.DocumentType;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.NonNull;

/**
 * Request body for the PATCH /{id}/document-type endpoint.
 *
 * @param documentType the new document type (must not be null)
 */
public record DocumentTypeRequest(@NotNull @NonNull DocumentType documentType) {
}
