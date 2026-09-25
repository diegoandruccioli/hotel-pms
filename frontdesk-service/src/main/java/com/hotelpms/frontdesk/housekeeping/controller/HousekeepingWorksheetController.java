package com.hotelpms.frontdesk.housekeeping.controller;

import com.hotelpms.internalauth.security.TenantContext;

import com.hotelpms.frontdesk.housekeeping.dto.BusinessDateResponse;
import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingWorksheetResponse;
import com.hotelpms.frontdesk.housekeeping.service.BusinessDateResolver;
import com.hotelpms.frontdesk.housekeeping.service.HousekeepingWorksheetService;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The housekeeping worksheet — a printable, room-by-room cleaning list for a
 * given business date, downloadable as far ahead or behind as staff need
 * (see {@code HousekeepingWorksheetServiceImpl} for the provisional/definitive
 * distinction that makes an early download safe).
 */
@RestController
@RequestMapping("/api/v1/frontdesk/housekeeping")
@RequiredArgsConstructor
public class HousekeepingWorksheetController {

    private static final String ROLE_ADMIN_OWNER_OR_RECEPTIONIST = "hasAnyRole('ADMIN', 'OWNER', 'RECEPTIONIST')";
    private static final String PDF_FILENAME_PREFIX = "pulizie-";
    private static final String PDF_EXTENSION = ".pdf";

    private final HousekeepingWorksheetService housekeepingWorksheetService;
    private final BusinessDateResolver businessDateResolver;
    private final HotelSettingsService hotelSettingsService;

    /**
     * Returns the worksheet as JSON — lets the UI show what's on it (and warn
     * on a date/provisional mismatch) before the user commits to a download.
     *
     * @param date the business date to build the worksheet for; defaults to
     *             the resolved business date when omitted
     * @return the worksheet
     */
    @GetMapping("/worksheet")
    @PreAuthorize(ROLE_ADMIN_OWNER_OR_RECEPTIONIST)
    public ResponseEntity<HousekeepingWorksheetResponse> getWorksheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        return ResponseEntity.ok(
                housekeepingWorksheetService.getWorksheet(TenantContext.resolveHotelId(), date));
    }

    /**
     * Downloads the worksheet as a printable PDF. A plain {@code GET} on
     * purpose — the frontend downloads it via a hidden iframe (cookie auth
     * through the gateway), which can't attach an {@code Authorization}
     * header.
     *
     * @param date the business date to build the worksheet for; defaults to
     *             the resolved business date when omitted
     * @return the PDF bytes with {@code Content-Disposition: attachment}
     */
    @GetMapping(value = "/worksheet.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLE_ADMIN_OWNER_OR_RECEPTIONIST)
    public ResponseEntity<byte[]> getWorksheetPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final UUID hotelId = TenantContext.resolveHotelId();
        final LocalDate resolvedDate = date != null ? date : businessDateResolver.resolve(hotelId);
        final byte[] pdf = housekeepingWorksheetService.getWorksheetPdf(hotelId, date);
        final ContentDisposition disposition = ContentDisposition.attachment()
                .filename(PDF_FILENAME_PREFIX + resolvedDate + PDF_EXTENSION)
                .build();
        return ResponseEntity.ok()
                .headers(h -> h.setContentDisposition(disposition))
                .body(pdf);
    }

    /**
     * Returns the hotel's currently resolved housekeeping business date, plus
     * the timezone/cutoff settings behind it — so the frontend's date picker
     * can default to the right day without re-implementing the cutoff rule.
     *
     * @return the resolved business date and the settings behind it
     */
    @GetMapping("/business-date")
    @PreAuthorize(ROLE_ADMIN_OWNER_OR_RECEPTIONIST)
    public ResponseEntity<BusinessDateResponse> getBusinessDate() {
        final UUID hotelId = TenantContext.resolveHotelId();
        final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(hotelId);
        return ResponseEntity.ok(new BusinessDateResponse(
                businessDateResolver.resolve(hotelId), settings.timezone(), settings.housekeepingDayCutoffHour()));
    }
}
