package com.hotelpms.frontdesk.stays.dto;

import java.time.LocalDate;

/**
 * Response carrying the last check-in date for a guest within a hotel.
 * Used by the guest-service GDPR legal-hold guard to verify the TULPS
 * (Testo Unico delle Leggi di Pubblica Sicurezza) five-year retention obligation.
 *
 * @param hasStays     {@code true} if the guest has at least one recorded stay
 * @param lastStayDate the most recent actual check-in date, or {@code null} if
 *                     {@code hasStays} is {@code false}
 */
public record GuestLastStayResponse(boolean hasStays, LocalDate lastStayDate) {
}
