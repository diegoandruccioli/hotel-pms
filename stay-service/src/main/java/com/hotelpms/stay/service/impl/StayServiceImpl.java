package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.client.BillingClient;
import com.hotelpms.stay.client.GuestClient;
import com.hotelpms.stay.client.InventoryClient;
import com.hotelpms.stay.client.ReservationClient;
import com.hotelpms.stay.client.dto.InvoiceStatusResponse;
import com.hotelpms.stay.client.dto.ReservationStatusUpdateRequest;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.domain.StayStatus;
import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import com.hotelpms.stay.exception.BillingNotPaidException;
import com.hotelpms.stay.exception.ExternalServiceException;
import com.hotelpms.stay.exception.NotFoundException;
import com.hotelpms.stay.mapper.StayMapper;
import com.hotelpms.stay.repository.StayRepository;
import com.hotelpms.stay.service.StayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the StayService interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StayServiceImpl implements StayService {

    private static final String PAID_STATUS = "PAID";

    private final StayRepository stayRepository;
    private final StayMapper stayMapper;
    private final GuestClient guestClient;
    private final ReservationClient reservationClient;
    private final InventoryClient inventoryClient;
    private final BillingClient billingClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkIn(final StayRequest request) {
        log.info("Processing check-in for reservation: {}", request.reservationId());

        try {
            log.debug("Validating guest ID: {}", request.guestId());
            guestClient.getGuestById(request.guestId());

            log.debug("Validating reservation ID: {}", request.reservationId());
            reservationClient.getReservationById(request.reservationId());

            log.debug("Validating room ID: {}", request.roomId());
            inventoryClient.getRoomById(request.roomId());
        } catch (final feign.FeignException ex) {
            log.error("External validation failed during check-in: {}", ex.getMessage());
            throw new ExternalServiceException("EXTERNAL_SERVICE_UNAVAILABLE: " + ex.getMessage(), ex);
        }

        final Stay newStay = stayMapper.toEntity(request);

        if (newStay.getGuests() != null) {
            newStay.getGuests().forEach(guest -> guest.setStay(newStay));
        }

        if (newStay.getActualCheckInTime() == null) {
            newStay.setActualCheckInTime(LocalDateTime.now());
        }

        if (newStay.getStatus() == null || newStay.getStatus() == StayStatus.EXPECTED) {
            newStay.setStatus(StayStatus.CHECKED_IN);
        }

        final Stay savedStay = stayRepository.save(newStay);
        log.info("Check-in successful. Stay ID: {}", savedStay.getId());

        // Update reservation actualGuests (sum of all StayGuest entries for this
        // reservation)
        updateReservationGuests(savedStay.getReservationId());

        return stayMapper.toDto(savedStay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StayResponse checkOut(@NonNull final UUID stayId) {
        log.info("Processing check-out for stay ID: {}", stayId);

        final Stay stay = stayRepository.findById(stayId)
                .orElseThrow(() -> new NotFoundException("STAY_NOT_FOUND"));

        if (stay.getStatus() != StayStatus.CHECKED_IN) {
            throw new IllegalStateException("INVALID_STAY_STATUS");
        }

        // 1. Verify billing folio is PAID
        log.debug("Verifying billing folio for reservation: {}", stay.getReservationId());
        final InvoiceStatusResponse invoice = billingClient.getLatestInvoiceByReservation(stay.getReservationId());
        if (invoice == null || !PAID_STATUS.equalsIgnoreCase(invoice.status())) {
            throw new BillingNotPaidException("BILLING_NOT_PAID");
        }

        // 2. Mark the room as DIRTY via the Inventory Service
        log.debug("Marking room {} as DIRTY after check-out", stay.getRoomId());
        inventoryClient.updateRoomStatus(stay.getRoomId(), "DIRTY");

        // 3. Update the stay entity
        stay.setStatus(StayStatus.CHECKED_OUT);
        stay.setActualCheckOutTime(LocalDateTime.now());

        final Stay updatedStay = stayRepository.save(stay);
        log.info("Check-out successful for stay ID: {}", stayId);

        return stayMapper.toDto(updatedStay);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public StayResponse getStayById(@NonNull final UUID id) {
        log.debug("Fetching stay by ID: {}", id);
        return stayRepository.findById(id)
                .map(stayMapper::toDto)
                .orElseThrow(() -> new NotFoundException("STAY_NOT_FOUND"));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getAllStays(final Pageable pageable) {
        log.debug("Fetching paginated stays, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return stayRepository.findAll(pageable).map(stayMapper::toDto);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<StayResponse> getStaysByReservationId(@NonNull final UUID reservationId, final Pageable pageable) {
        log.debug("Fetching stays for reservationId: {}", reservationId);
        final List<Stay> stays = stayRepository.findAllByReservationId(reservationId);
        final List<StayResponse> content = stays.stream()
                .map(stayMapper::toDto)
                .toList();
        return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, content.size());
    }

    private void updateReservationGuests(final UUID reservationId) {
        if (reservationId == null) {
            return;
        }
        final com.hotelpms.stay.client.dto.ReservationResponse res =
                reservationClient.getReservationById(reservationId);
        final List<Stay> stays = stayRepository.findAllByReservationId(reservationId);

        final int actualGuests = stays.stream()
                .mapToInt(stay -> stay.getGuests() == null ? 0 : stay.getGuests().size())
                .sum();
        String status = null;
        if (res.lineItems() != null) {
            final int totalRooms = res.lineItems().size();
            final int checkedInRooms = stays.size();

            if (checkedInRooms >= totalRooms && totalRooms > 0) {
                status = "CHECKED_IN";
            } else if (checkedInRooms > 0) {
                status = "PARTIALLY_CHECKED_IN";
            }
        }

        reservationClient.updateStatusAndGuests(reservationId,
                new ReservationStatusUpdateRequest(status, actualGuests));
    }
}
