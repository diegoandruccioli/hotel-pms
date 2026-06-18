package com.hotelpms.frontdesk.stays.service;

import com.hotelpms.frontdesk.stays.dto.AlloggiatiRowDto;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for generating Italian Alloggiati Web police reports and structured exports.
 */
public interface AlloggiatiReportService {

    /**
     * Generates a pipe-delimited Alloggiati Web report for all guests
     * who checked in on the given date.
     *
     * @param date the check-in date to generate the report for
     * @return pipe-delimited report content as a UTF-8 string
     */
    String generateReport(LocalDate date);

    /**
     * Generates a structured list of Alloggiati rows for the given date,
     * suitable for JSON export and integration with channel managers,
     * accounting software, and BI tools.
     *
     * @param date the check-in date to generate the export for
     * @return list of guest arrival records
     */
    List<AlloggiatiRowDto> generateJsonReport(LocalDate date);
}
