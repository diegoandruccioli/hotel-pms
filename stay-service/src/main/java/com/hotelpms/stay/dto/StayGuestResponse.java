package com.hotelpms.stay.dto;

import com.hotelpms.stay.domain.TravellerType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Data Transfer Object for responding with StayGuest data.
 *
 * @param id                   the unique ID
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
public record StayGuestResponse(
        UUID id,
        String firstName,
        String lastName,
        String gender,
        LocalDate dateOfBirth,
        String placeOfBirth,
        String citizenship,
        String documentType,
        String documentNumber,
        String documentPlaceOfIssue,
        boolean isPrimaryGuest,
        TravellerType travellerType,
        String travelPurpose
) {
}
