package com.hotelpms.reservation.controller;

import com.hotelpms.reservation.dto.ReservationRequest;
import com.hotelpms.reservation.dto.ReservationResponse;
import com.hotelpms.reservation.dto.ReservationStatusUpdateRequest;
import com.hotelpms.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for Reservations.
 */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ReservationService reservationService;

    /**
     * Creates a reservation.
     *
     * @param request the request
     * @return the response
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @NonNull @Valid @RequestBody final ReservationRequest request) {
        final ReservationResponse response = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a reservation by id.
     *
     * @param id the id
     * @return the response
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationById(@NonNull @PathVariable final UUID id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    /**
     * Gets a paginated list of all reservations.
     * Supports standard Spring Data pagination query parameters:
     * {@code ?page=0&size=20&sort=checkInDate,desc}
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of reservation responses
     */
    @GetMapping
    public ResponseEntity<Page<ReservationResponse>> getAllReservations(
            @PageableDefault(
                    size = DEFAULT_PAGE_SIZE,
                    sort = "checkInDate",
                    direction = Sort.Direction.DESC
            ) final Pageable pageable) {
        return ResponseEntity.ok(reservationService.getAllReservations(pageable));
    }

    /**
     * Updates a reservation.
     *
     * @param id      the id
     * @param request the request
     * @return the response
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponse> updateReservation(@NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final ReservationRequest request) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    /**
     * Deletes a reservation.
     *
     * @param id the id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReservation(@NonNull @PathVariable final UUID id) {
        reservationService.deleteReservation(id);
    }

    /**
     * Partially updates reservation status and/or actual guests.
     *
     * @param id      the reservation id
     * @param request status/guests payload
     * @return the updated reservation
     */
    @PatchMapping("/{id}/status-and-guests")
    public ResponseEntity<ReservationResponse> updateStatusAndGuests(
            @NonNull @PathVariable final UUID id,
            @NonNull @RequestBody final ReservationStatusUpdateRequest request) {
        final ReservationResponse response = reservationService.updateStatusAndGuests(
                id, request.status(), request.actualGuests());
        return ResponseEntity.ok(response);
    }
}
