package com.hotelpms.stay.client.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Enriched client DTO for fetching full guest details from the Guest Service,
 * including identity documents needed for Alloggiati Web police reporting.
 *
 * @param id                the guest's unique identifier
 * @param firstName         the guest's first name
 * @param lastName          the guest's last name
 * @param dateOfBirth       the guest's date of birth
 * @param identityDocuments the guest's identity documents
 */
public record AlloggiatiGuestResponse(
                UUID id,
                String firstName,
                String lastName,
                LocalDate dateOfBirth,
                List<AlloggiatiDocumentResponse> identityDocuments) {

        /**
         * Core constructor with defensive copy for identity documents list.
         *
         * @param id                the guest's unique identifier
         * @param firstName         the guest's first name
         * @param lastName          the guest's last name
         * @param dateOfBirth       the guest's date of birth
         * @param identityDocuments the guest's identity documents
         */
        public AlloggiatiGuestResponse {
                identityDocuments = List.copyOf(identityDocuments);
        }

        /**
         * Nested record representing a single identity document entry.
         *
         * @param documentType   the type of document (e.g. PASSPORT, ID_CARD)
         * @param documentNumber the document number
         */
        public record AlloggiatiDocumentResponse(
                        String documentType,
                        String documentNumber) {
        }
}
