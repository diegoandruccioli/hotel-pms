package com.hotelpms.stay.service;

import java.time.LocalDate;

/**
 * Service for generating Italian Alloggiati Web police reports.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AlloggiatiReportService {

    /**
     * Generates a pipe-delimited Alloggiati Web report for all guests
     * who checked in on the given date.
     *
     * @param date the check-in date to generate the report for
     * @return pipe-delimited report content as a UTF-8 string
     */
    String generateReport(LocalDate date);
}
