package com.hotelpms.frontdesk.stays.service;

import com.hotelpms.frontdesk.stays.dto.StayGuestRequest;
import com.hotelpms.frontdesk.stays.dto.StayGuestResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Guest-lifecycle operations on an open ({@code CHECKED_IN}) stay (Parte 1):
 * add a late arrival, correct a rejected document, record an early
 * departure, or change who's primary. All operations are scoped to the
 * caller's hotel and require the stay to be {@code CHECKED_IN} —
 * see {@code StayGuestValidator}.
 */
public interface StayGuestService {

    /**
     * Adds a guest to an open stay — the late-arrival case (one guest checks in at
     * 15:00, the other joins at night) and the mid-stay-addition case (a partner
     * joins for the last nights). Stamps {@code arrivalDate = today} and triggers a
     * tourist-tax rectification for the guest's remaining nights, if the stay is
     * already assessed.
     *
     * @param stayId  the stay to add the guest to
     * @param hotelId the caller's hotel UUID
     * @param request the new guest's data
     * @return the created guest
     */
    StayGuestResponse addGuest(UUID stayId, UUID hotelId, StayGuestRequest request);

    /**
     * Corrects an existing guest's anagraphic/document data — e.g. Alloggiati Web
     * rejected a schedina for a bad document number. If the guest was already sent,
     * flags {@code needsResubmit} rather than silently losing the correction: the
     * portal has no rectification API, only full resubmission.
     *
     * @param stayId  the stay the guest belongs to
     * @param guestId the guest to correct
     * @param hotelId the caller's hotel UUID
     * @param request the corrected data
     * @return the updated guest
     */
    StayGuestResponse updateGuest(UUID stayId, UUID guestId, UUID hotelId, StayGuestRequest request);

    /**
     * Removes a guest — only allowed before their schedina was ever sent and when
     * they aren't the stay's primary guest (a typo caught right after entry). A
     * guest already transmitted, or leaving early, must go through {@link
     * #recordDeparture} instead: Alloggiati Web has no cancellation, only an
     * accurate departure date.
     *
     * @param stayId  the stay the guest belongs to
     * @param guestId the guest to remove
     * @param hotelId the caller's hotel UUID
     */
    void removeGuest(UUID stayId, UUID guestId, UUID hotelId);

    /**
     * Records an early departure for one guest of a multi-guest stay, without
     * checking out the room itself.
     *
     * @param stayId        the stay the guest belongs to
     * @param guestId       the departing guest
     * @param hotelId       the caller's hotel UUID
     * @param departureDate the departure date (must not precede the guest's arrival)
     * @return the updated guest
     */
    StayGuestResponse recordDeparture(UUID stayId, UUID guestId, UUID hotelId, LocalDate departureDate);

    /**
     * Promotes another guest of the stay to primary (e.g. the original primary
     * guest departs early). Demotes every other guest on the stay.
     *
     * @param stayId  the stay the guest belongs to
     * @param guestId the guest to promote
     * @param hotelId the caller's hotel UUID
     * @return the promoted guest
     */
    StayGuestResponse promotePrimary(UUID stayId, UUID guestId, UUID hotelId);
}
