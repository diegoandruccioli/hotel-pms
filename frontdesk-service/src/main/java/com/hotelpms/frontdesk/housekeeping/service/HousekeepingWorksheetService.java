package com.hotelpms.frontdesk.housekeeping.service;

import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingWorksheetResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds the housekeeping worksheet — the printable, room-by-room list of
 * what needs cleaning for a given business date.
 */
public interface HousekeepingWorksheetService {

    /**
     * Builds the worksheet for a hotel and date.
     *
     * @param hotelId the hotel to build the worksheet for
     * @param date    the business date to build it for; {@code null} resolves
     *                to {@link BusinessDateResolver#resolve}
     * @return the worksheet
     */
    HousekeepingWorksheetResponse getWorksheet(UUID hotelId, LocalDate date);

    /**
     * Renders the worksheet for a hotel and date as a printable PDF.
     *
     * @param hotelId the hotel to build the worksheet for
     * @param date    the business date to build it for; {@code null} resolves
     *                to {@link BusinessDateResolver#resolve}
     * @return the rendered PDF bytes
     */
    byte[] getWorksheetPdf(UUID hotelId, LocalDate date);
}
