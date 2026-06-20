package com.hotelpms.frontdesk.stays.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service responsible for submitting the Italian Alloggiati Web police report
 * to the Polizia di Stato portal over a TLS-verified HTTPS channel.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AlloggiatiWebSenderService {

    /**
     * Generates the Alloggiati Web report for the given date and hotel, and submits
     * it to the Polizia di Stato portal via HTTPS with certificate chain validation.
     *
     * @param date    the check-in date to report
     * @param hotelId the hotel UUID (tenant isolation) — each hotel reports under its
     *                own PS portal credentials, so this submission must never include
     *                another hotel's guests
     * @throws com.hotelpms.frontdesk.exception.ExternalServiceException if the portal
     *                                                              is unreachable or returns an error
     */
    void submitReport(LocalDate date, UUID hotelId);
}
