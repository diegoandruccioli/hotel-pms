package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validates stay/guest state ahead of every guest-lifecycle mutation (Parte 1):
 * the stay must belong to the caller's hotel and be {@code CHECKED_IN} — a
 * guest can't be added, corrected, departed, or promoted before check-in
 * (nothing to attach them to yet) or after check-out (the stay is closed).
 */
@Component
@RequiredArgsConstructor
class StayGuestValidator {

    private static final String STAY_NOT_FOUND_MSG = "STAY_NOT_FOUND";
    private static final String STAY_GUEST_NOT_FOUND_MSG = "STAY_GUEST_NOT_FOUND";

    private final StayRepository stayRepository;

    /**
     * Loads the stay scoped to the caller's hotel and verifies it's open for guest
     * mutations.
     *
     * @param stayId  the stay UUID
     * @param hotelId the caller's hotel UUID (tenant isolation)
     * @return the stay, guaranteed {@code CHECKED_IN} and belonging to {@code hotelId}
     */
    Stay validateStayForGuestMutation(final UUID stayId, final UUID hotelId) {
        final Stay stay = stayRepository.findByIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException(STAY_NOT_FOUND_MSG));
        if (stay.getStatus() != StayStatus.CHECKED_IN) {
            throw new IllegalStateException("INVALID_STAY_STATUS");
        }
        return stay;
    }

    /**
     * Finds a guest belonging to the given stay, or 404s — guarding against a
     * guest id from a different stay (or a different hotel entirely).
     *
     * @param stay    the stay the guest must belong to
     * @param guestId the guest UUID
     * @return the matching guest
     */
    StayGuest findGuestOnStay(final Stay stay, final UUID guestId) {
        return stay.getGuests().stream()
                .filter(g -> g.getId().equals(guestId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(STAY_GUEST_NOT_FOUND_MSG));
    }
}
