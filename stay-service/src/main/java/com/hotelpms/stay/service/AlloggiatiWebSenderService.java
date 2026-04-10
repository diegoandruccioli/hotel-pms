package com.hotelpms.stay.service;

import java.time.LocalDate;

/**
 * Service responsible for submitting the Italian Alloggiati Web police report
 * to the Polizia di Stato portal over a TLS-verified HTTPS channel.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AlloggiatiWebSenderService {

    /**
     * Generates the Alloggiati Web report for the given date and submits it to
     * the Polizia di Stato portal via HTTPS with certificate chain validation.
     *
     * @param date the check-in date to report
     * @throws com.hotelpms.stay.exception.ExternalServiceException if the portal
     *                                                              is unreachable or returns an error
     */
    void submitReport(LocalDate date);
}
