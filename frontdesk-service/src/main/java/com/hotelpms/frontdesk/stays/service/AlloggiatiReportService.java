package com.hotelpms.frontdesk.stays.service;

import com.hotelpms.frontdesk.stays.dto.AlloggiatiRowDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating Italian Alloggiati Web police reports and structured exports.
 */
public interface AlloggiatiReportService {

    /**
     * Generates the fixed-width (168 char/record) Alloggiati Web report for all guests
     * who checked in on the given date, scoped to the given hotel.
     *
     * @param date    the check-in date to generate the report for
     * @param hotelId the hotel UUID (tenant isolation)
     * @return fixed-width report content as a UTF-8 string
     */
    String generateReport(LocalDate date, UUID hotelId);

    /**
     * Generates a structured list of Alloggiati rows for the given date and hotel,
     * suitable for JSON export and integration with channel managers,
     * accounting software, and BI tools.
     *
     * @param date    the check-in date to generate the export for
     * @param hotelId the hotel UUID (tenant isolation)
     * @return list of guest arrival records
     */
    List<AlloggiatiRowDto> generateJsonReport(LocalDate date, UUID hotelId);
}
