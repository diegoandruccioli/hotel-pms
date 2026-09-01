package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.dto.StayGuestRequest;
import com.hotelpms.frontdesk.stays.dto.StayGuestResponse;
import com.hotelpms.frontdesk.stays.mapper.StayGuestMapper;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.StayGuestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementation of {@link StayGuestService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StayGuestServiceImpl implements StayGuestService {

    private static final String ALREADY_SENT_MSG = "STAY_GUEST_ALREADY_SENT";
    private static final String IS_PRIMARY_MSG = "STAY_GUEST_IS_PRIMARY";
    private static final String DEPARTURE_BEFORE_ARRIVAL_MSG = "STAY_GUEST_DEPARTURE_BEFORE_ARRIVAL";
    private static final String STAY_GUEST_STALE_VERSION_MSG = "STAY_GUEST_STALE_VERSION";

    private final StayRepository stayRepository;
    private final StayGuestValidator stayGuestValidator;
    private final StayGuestMapper stayGuestMapper;
    private final CityTaxAssessmentService cityTaxAssessmentService;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayGuestResponse addGuest(final UUID stayId, final UUID hotelId, final StayGuestRequest request) {
        final Stay stay = stayGuestValidator.validateStayForGuestMutation(stayId, hotelId);
        final StayGuest guest = stayGuestMapper.toEntity(request);
        guest.setStay(stay);
        guest.setArrivalDate(LocalDate.now());
        stay.getGuests().add(guest);
        stayRepository.save(stay);
        log.info("[STAY] GUEST_ADDED | stayId={} | hotelId={}", stayId, hotelId);

        // Stamps guest.cityTaxChargeId/cityTaxChargeAmount in place when a charge was
        // actually posted — persisted by the save below so a later removeGuest can
        // void exactly this charge.
        cityTaxAssessmentService.rectifyForGuestAdded(stay, guest);
        stayRepository.save(stay);

        return stayGuestMapper.toResponse(guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayGuestResponse updateGuest(
            final UUID stayId, final UUID guestId, final UUID hotelId, final StayGuestRequest request) {
        final Stay stay = stayGuestValidator.validateStayForGuestMutation(stayId, hotelId);
        final StayGuest guest = stayGuestValidator.findGuestOnStay(stay, guestId);
        verifyNotStale(guest, request.version());
        final boolean wasSent = guest.isAlloggiatiSent();
        stayGuestMapper.updateEntityFromRequest(request, guest);
        if (wasSent) {
            // Alloggiati Web has no rectification API — a correction after the original
            // send is never silent, it must go out again on the next report run.
            guest.setNeedsResubmit(true);
        }
        stayRepository.save(stay);
        log.info("[STAY] GUEST_UPDATED | stayId={} | guestId={} | needsResubmit={}",
                stayId, guestId, guest.isNeedsResubmit());
        return stayGuestMapper.toResponse(guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void removeGuest(final UUID stayId, final UUID guestId, final UUID hotelId) {
        final Stay stay = stayGuestValidator.validateStayForGuestMutation(stayId, hotelId);
        final StayGuest guest = stayGuestValidator.findGuestOnStay(stay, guestId);
        if (guest.isAlloggiatiSent()) {
            throw new ConflictException(ALREADY_SENT_MSG);
        }
        if (guest.isPrimaryGuest()) {
            throw new ConflictException(IS_PRIMARY_MSG);
        }
        stay.getGuests().remove(guest);
        stayRepository.save(stay);
        log.info("[STAY] GUEST_REMOVED | stayId={} | guestId={}", stayId, guestId);

        cityTaxAssessmentService.rectifyForGuestRemoved(stay, guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayGuestResponse recordDeparture(
            final UUID stayId, final UUID guestId, final UUID hotelId, final LocalDate departureDate) {
        final Stay stay = stayGuestValidator.validateStayForGuestMutation(stayId, hotelId);
        final StayGuest guest = stayGuestValidator.findGuestOnStay(stay, guestId);
        if (departureDate.isBefore(guest.getArrivalDate())) {
            throw new BadRequestException(DEPARTURE_BEFORE_ARRIVAL_MSG);
        }
        guest.setDepartureDate(departureDate);
        stayRepository.save(stay);
        log.info("[STAY] GUEST_DEPARTED | stayId={} | guestId={} | departureDate={}", stayId, guestId, departureDate);
        return stayGuestMapper.toResponse(guest);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayGuestResponse promotePrimary(final UUID stayId, final UUID guestId, final UUID hotelId) {
        final Stay stay = stayGuestValidator.validateStayForGuestMutation(stayId, hotelId);
        final StayGuest newPrimary = stayGuestValidator.findGuestOnStay(stay, guestId);
        stay.getGuests().forEach(g -> g.setPrimaryGuest(g.getId().equals(guestId)));
        stayRepository.save(stay);
        log.info("[STAY] GUEST_PROMOTED_PRIMARY | stayId={} | guestId={}", stayId, guestId);
        return stayGuestMapper.toResponse(newPrimary);
    }

    /**
     * Rejects a stale guest edit — mirrors {@code ReservationServiceImpl#verifyNotStale}:
     * a client that read this guest at an earlier version must fail fast if someone
     * else has already saved a correction since. {@code null} skips the check (a
     * client that doesn't send a version yet).
     *
     * @param guest         the guest as currently persisted
     * @param clientVersion the version the client last read, or {@code null} to skip
     */
    private static void verifyNotStale(final StayGuest guest, final Long clientVersion) {
        if (clientVersion != null && !clientVersion.equals(guest.getVersion())) {
            throw new ConflictException(STAY_GUEST_STALE_VERSION_MSG);
        }
    }
}
