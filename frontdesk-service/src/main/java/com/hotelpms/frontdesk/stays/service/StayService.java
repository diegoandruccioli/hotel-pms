package com.hotelpms.frontdesk.stays.service;

import com.hotelpms.frontdesk.stays.dto.GuestLastStayResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.dto.StaySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Stay operations.
 */
public interface StayService {

    /**
     * Creates a new stay (Check-in). Orchestrates calls to the rooms/reservations
     * domains (in-process) and guest-service/billing-service (Feign) to validate.
     *
     * @param request the stay request details
     * @return the created stay response
     */
    StayResponse checkIn(StayRequest request);

    /**
     * Performs a guest check-out. Updates stay status, marks the room as DIRTY,
     * and verifies the billing folio is PAID.
     *
     * @param stayId  the ID of the stay to check out
     * @param hotelId the authenticated hotel UUID; the stay must belong to it (T-STAY-04)
     * @return the updated stay response
     */
    StayResponse checkOut(@NonNull UUID stayId, @NonNull UUID hotelId);

    /**
     * Gets a stay by its ID, scoped to the authenticated hotel.
     *
     * @param id      the stay ID
     * @param hotelId the authenticated hotel UUID; the stay must belong to it (T-STAY-04)
     * @return the stay response
     */
    StayResponse getStayById(@NonNull UUID id, @NonNull UUID hotelId);

    /**
     * Retrieves a paginated list of all stays belonging to the authenticated hotel.
     * Supports standard Spring Data pagination query parameters.
     *
     * @param pageable the pagination and sorting parameters
     * @param hotelId  the authenticated hotel UUID (T-STAY-04)
     * @return a page of stay responses
     */
    Page<StayResponse> getAllStays(Pageable pageable, @NonNull UUID hotelId);

    /**
     * Retrieves all stays for a given reservation, scoped to the authenticated hotel.
     *
     * @param reservationId the reservation ID
     * @param hotelId       the authenticated hotel UUID; the reservation must belong to it (T-STAY-04)
     * @param pageable      the pagination and sorting parameters
     * @return list of stay responses
     */
    Page<StayResponse> getStaysByReservationId(@NonNull UUID reservationId, @NonNull UUID hotelId, Pageable pageable);

    /**
     * Returns the most recent completed stay for a guest, for check-in form pre-filling.
     * Verifies the guest profile is still active in guest-service before returning data
     * (fail-safe: returns empty if the profile was anonymised or the service is unavailable).
     *
     * @param guestId the guest UUID
     * @return an Optional containing the last CHECKED_OUT stay, or empty if none or guest inactive
     */
    Optional<StayResponse> getLastCompletedStayForGuest(@NonNull UUID guestId);

    /**
     * Returns the most recent check-in date for a guest within a hotel.
     * Called by the guest-service GDPR legal-hold guard (T-GST-05) to verify
     * whether the TULPS five-year retention obligation has expired before
     * anonymising a guest profile.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @return a response containing whether stays exist and the most recent date
     */
    GuestLastStayResponse getLastStayDateForGuest(@NonNull UUID guestId, @NonNull UUID hotelId);

    /**
     * Returns all stay summaries for a guest within a hotel, ordered by check-in
     * descending. Used by the GDPR Art. 20 data-export endpoint.
     *
     * @param guestId the guest UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @return list of stay summaries, most recent first
     */
    List<StaySummaryResponse> getStayHistoryForGuest(@NonNull UUID guestId, @NonNull UUID hotelId);
}
