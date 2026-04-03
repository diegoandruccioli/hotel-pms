package com.hotelpms.stay.controller;

import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import com.hotelpms.stay.service.AlloggiatiReportService;
import com.hotelpms.stay.service.StayService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * REST Controller for Stay operations.
 */
@RestController
@RequestMapping("/api/v1/stays")
@RequiredArgsConstructor
public class StayController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final StayService stayService;
    private final AlloggiatiReportService alloggiatiReportService;

    /**
     * Endpoint to check in a guest and create a stay.
     *
     * @param request the stay request
     * @return the created stay response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StayResponse checkIn(@NonNull @Valid @RequestBody final StayRequest request) {
        return stayService.checkIn(request);
    }

    /**
     * Endpoint to check out a guest from a stay.
     * Verifies billing is PAID and marks the room as DIRTY.
     *
     * @param id the stay ID
     * @return the updated stay response
     */
    @PutMapping("/{id}/check-out")
    public StayResponse checkOut(@NonNull @PathVariable("id") final UUID id) {
        return stayService.checkOut(id);
    }

    /**
     * Endpoint to get a stay by its ID.
     *
     * @param id the stay ID
     * @return the stay response
     */
    @GetMapping("/{id}")
    public StayResponse getStayById(@NonNull @PathVariable("id") final UUID id) {
        return stayService.getStayById(id);
    }

    /**
     * Retrieves a paginated list of all stays.
     * Supports standard Spring Data pagination query parameters:
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
        if (reservationId != null) {
            return ResponseEntity.ok(stayService.getStaysByReservationId(reservationId, pageable));
        }
        return ResponseEntity.ok(stayService.getAllStays(pageable));
    }

    /**
     * Generates and downloads the Italian Alloggiati Web police report for all
     * guests who checked in on the given date.
     *
     * @param date the check-in date in YYYY-MM-DD format
     * @return the downloadable pipe-delimited text report
     */
    @GetMapping("/reports/alloggiati")
    @SuppressWarnings("PMD.LooseCoupling")
    public ResponseEntity<byte[]> downloadAlloggiatiReport(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final String content = alloggiatiReportService.generateReport(date);
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
}
