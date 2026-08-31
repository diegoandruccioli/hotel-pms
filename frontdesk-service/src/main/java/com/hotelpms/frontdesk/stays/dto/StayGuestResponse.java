package com.hotelpms.frontdesk.stays.dto;

import com.hotelpms.frontdesk.stays.domain.TravellerType;
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
 * @param arrivalDate          the date this guest actually arrived (Parte 1)
 * @param departureDate        this guest's own departure date, if different from the room's
 * @param alloggiatiSent       whether this guest's own Alloggiati Web schedina was transmitted
 * @param needsResubmit        whether a correction after sending still needs to be retransmitted
 * @param version              the version to echo back on {@code updateGuest} for
 *                             optimistic-lock conflict detection
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
        String travelPurpose,
        LocalDate arrivalDate,
        LocalDate departureDate,
        boolean alloggiatiSent,
        boolean needsResubmit,
        Long version
) {
}
