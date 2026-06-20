package com.hotelpms.frontdesk.stays.controller;

import com.hotelpms.frontdesk.stays.dto.AlloggiatiFailureSummaryResponse;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiRowDto;
import com.hotelpms.frontdesk.stays.dto.GuestLastStayResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import com.hotelpms.frontdesk.stays.dto.StaySummaryResponse;
import com.hotelpms.frontdesk.stays.service.AlloggiatiReportService;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.StayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller for Stay operations.
 */
@RestController
@RequestMapping("/api/v1/stays")
@RequiredArgsConstructor
public class StayController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String ROLE_ADMIN_OR_OWNER = "hasAnyRole('ADMIN', 'OWNER')";

    private final StayService stayService;
    private final AlloggiatiReportService alloggiatiReportService;
    private final AlloggiatiWebSenderService alloggiatiWebSenderService;

    /**
     * Endpoint to check in a guest and create a stay.
     * The {@code hotelId} is always taken from the gateway-injected {@code X-Auth-Hotel}
     * header (via SecurityContext) to enforce multi-tenant isolation — any value
     * present in the request body is overridden.
     *
     * @param request the stay request
     * @return the created stay response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StayResponse checkIn(@NonNull @Valid @RequestBody final StayRequest request) {
        final UUID hotelId = Objects.requireNonNull(extractHotelId());
        final StayRequest enriched = new StayRequest(
                hotelId,
                request.reservationId(),
                request.guestId(),
                request.roomId(),
                request.status(),
                request.expectedCheckOutDate(),
                request.actualCheckInTime(),
                request.actualCheckOutTime(),
                request.guests());
        return stayService.checkIn(enriched);
    }

    /**
     * Endpoint to check out a guest from a stay.
     * Verifies billing is PAID and marks the room as DIRTY. Scoped to the
     * caller's hotel (T-STAY-04): a stay belonging to another hotel returns 404.
     *
     * @param id the stay ID
     * @return the updated stay response
     */
    @PutMapping("/{id}/check-out")
    public StayResponse checkOut(@NonNull @PathVariable("id") final UUID id) {
        return stayService.checkOut(id, Objects.requireNonNull(extractHotelId()));
    }

    /**
     * Endpoint to get a stay by its ID, scoped to the caller's hotel
     * (T-STAY-04): a stay belonging to another hotel returns 404.
     *
     * @param id the stay ID
     * @return the stay response
     */
    @GetMapping("/{id}")
    public StayResponse getStayById(@NonNull @PathVariable("id") final UUID id) {
        return stayService.getStayById(id, Objects.requireNonNull(extractHotelId()));
    }

    /**
     * Retrieves a paginated list of all stays belonging to the caller's hotel
     * (T-STAY-04). Supports standard Spring Data pagination query parameters:
     * {@code ?page=0&size=20&sort=actualCheckInTime,desc}
     *
     * @param reservationId optional reservation ID to filter by
     * @param pageable      the pagination and sorting parameters
     * @return a page of stay responses
     */
    @GetMapping
    public ResponseEntity<Page<StayResponse>> getAllStays(
            @RequestParam(name = "reservationId", required = false) final UUID reservationId,
            @PageableDefault(size = DEFAULT_PAGE_SIZE,
                    sort = "actualCheckInTime",
                    direction = Sort.Direction.DESC)
            final Pageable pageable) {
        final UUID hotelId = Objects.requireNonNull(extractHotelId());
        if (reservationId != null) {
            return ResponseEntity.ok(stayService.getStaysByReservationId(reservationId, hotelId, pageable));
        }
        return ResponseEntity.ok(stayService.getAllStays(pageable, hotelId));
    }

    /**
     * Returns the most recent completed stay for a guest, used to pre-fill the check-in form.
     * Verifies that the guest profile is still active before returning any data (Option-B
     * GDPR safeguard). Returns 204 No Content when the guest has no previous stays, the
     * profile was anonymised, or guest-service is unreachable (fail-safe).
     *
     * @param guestId the guest UUID
     * @return 200 with the last completed stay, or 204 No Content
     */
    @GetMapping("/guest/{guestId}/latest")
    public ResponseEntity<StayResponse> getLastCompletedStayForGuest(
            @NonNull @PathVariable("guestId") final UUID guestId) {
        return stayService.getLastCompletedStayForGuest(guestId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Generates and downloads the Italian Alloggiati Web police report for all
     * guests who checked in on the given date, scoped to the caller's hotel.
     *
     * @param date the check-in date in YYYY-MM-DD format
     * @return the downloadable fixed-width text report
     */
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    @GetMapping("/reports/alloggiati")
    @SuppressWarnings("PMD.LooseCoupling")
    public ResponseEntity<byte[]> downloadAlloggiatiReport(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final String content = alloggiatiReportService.generateReport(date, Objects.requireNonNull(extractHotelId()));
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("alloggiati-" + date + ".txt")
                        .build());
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * Generates and downloads the Alloggiati data as a structured JSON export
     * for integration with channel managers, accounting software, and BI tools.
     *
     * @param date the check-in date in YYYY-MM-DD format
     * @return the downloadable JSON array of guest arrival records
     */
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    @GetMapping("/reports/alloggiati/json")
    @SuppressWarnings("PMD.LooseCoupling")
    public ResponseEntity<List<AlloggiatiRowDto>> downloadAlloggiatiJson(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final List<AlloggiatiRowDto> rows =
                alloggiatiReportService.generateJsonReport(date, Objects.requireNonNull(extractHotelId()));
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("alloggiati-" + date + ".json")
                        .build());
        return ResponseEntity.ok().headers(headers).body(rows);
    }

    /**
     * Submits the Alloggiati Web report for the given date to the Polizia di Stato
     * portal over a TLS-verified HTTPS channel (T-STAY-03).
     *
     * @param date the check-in date in YYYY-MM-DD format
     * @return 200 OK on successful transmission
     */
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    @PostMapping("/reports/alloggiati/submit")
    public ResponseEntity<Void> submitAlloggiatiReport(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final UUID hotelId = Objects.requireNonNull(extractHotelId());
        alloggiatiWebSenderService.submitReport(date, hotelId);
        stayService.markAlloggiatiSentForDate(date, hotelId);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns a summary of unresolved Alloggiati Web submission failures for the
     * caller's hotel, used to drive the Dashboard alert banner.
     *
     * @return the failure summary
     */
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    @GetMapping("/reports/alloggiati/failures/summary")
    public ResponseEntity<AlloggiatiFailureSummaryResponse> getAlloggiatiFailureSummary() {
        return ResponseEntity.ok(stayService.getAlloggiatiFailureSummary(Objects.requireNonNull(extractHotelId())));
    }

    /**
     * Returns the most recent check-in date for a guest within the caller's hotel.
     * Called by guest-service GDPR legal-hold guard (T-GST-05).
     *
     * @param guestId the guest UUID
     * @return response with existence flag and most recent check-in date
     */
    @GetMapping("/guest/{guestId}/last-date")
    public ResponseEntity<GuestLastStayResponse> getLastStayDateForGuest(
            @NonNull @PathVariable final UUID guestId) {
        final UUID hotelId = extractHotelId();
        return ResponseEntity.ok(
                stayService.getLastStayDateForGuest(guestId, Objects.requireNonNull(hotelId)));
    }

    /**
     * Returns the full stay history for a guest within the caller's hotel.
     * Called by guest-service GDPR Art. 20 data-export endpoint.
     *
     * @param guestId the guest UUID
     * @return list of stay summaries, most recent first
     */
    @GetMapping("/guest/{guestId}/history")
    public ResponseEntity<List<StaySummaryResponse>> getStayHistoryForGuest(
            @NonNull @PathVariable final UUID guestId) {
        final UUID hotelId = extractHotelId();
        return ResponseEntity.ok(
                stayService.getStayHistoryForGuest(guestId, Objects.requireNonNull(hotelId)));
    }

    private UUID extractHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
