package com.hotelpms.stay.dto;

import com.hotelpms.stay.domain.TravellerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Data Transfer Object for creating or updating a StayGuest.
 *
 * @param firstName            the first name of the guest
 * @param lastName             the last name of the guest
 * @param gender               the gender of the guest
 * @param dateOfBirth          the date of birth of the guest
 * @param placeOfBirth         the place of birth of the guest
 * @param citizenship          the citizenship of the guest
 * @param documentType         the document type
 * @param documentNumber       the document number
 * @param documentPlaceOfIssue the document issue location
 * @param isPrimaryGuest       whether the guest is the primary one
 * @param travellerType        the traveller classification type
 * @param travelPurpose        the purpose of travel
 */
public record StayGuestRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Gender is required") String gender,
        @NotNull(message = "Date of birth is required") LocalDate dateOfBirth,
        @NotBlank(message = "Place of birth is required") String placeOfBirth,
        @NotBlank(message = "Citizenship is required") String citizenship,
        @NotBlank(message = "Document type is required") String documentType,
        @NotBlank(message = "Document number is required") String documentNumber,
        @NotBlank(message = "Document place of issue is required") String documentPlaceOfIssue,
        boolean isPrimaryGuest,
        TravellerType travellerType,
        String travelPurpose
) {
}
