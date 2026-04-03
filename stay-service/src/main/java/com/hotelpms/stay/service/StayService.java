package com.hotelpms.stay.service;

import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import org.springframework.lang.NonNull;

/**
 * Service interface for Stay operations.
 */
public interface StayService {

    /**
     * Creates a new stay (Check-in). Orchestrates calls to external services to
     * validate.
     *
     * @param request the stay request details
     * @return the created stay response
     */
    StayResponse checkIn(StayRequest request);

    /**
     * Performs a guest check-out. Updates stay status, marks the room as DIRTY
     * via the Inventory Service, and verifies the billing folio is PAID.
     *
     * @param stayId the ID of the stay to check out
     * @return the updated stay response
     */
    StayResponse checkOut(@NonNull UUID stayId);

    /**
     * Gets a stay by its ID.
     *
     * @param id the stay ID
     * @return the stay response
     */
    StayResponse getStayById(@NonNull UUID id);

    /**
     * Retrieves a paginated list of all stays.
     * Supports standard Spring Data pagination query parameters.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of stay responses
     */
    Page<StayResponse> getAllStays(Pageable pageable);

    /**
     * Retrieves all stays for a given reservation.
     *
     * @param reservationId the reservation ID
     * @param pageable      the pagination and sorting parameters
     * @return list of stay responses
     */
    Page<StayResponse> getStaysByReservationId(@NonNull UUID reservationId, Pageable pageable);
}
