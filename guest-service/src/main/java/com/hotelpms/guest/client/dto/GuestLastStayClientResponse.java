package com.hotelpms.guest.client.dto;

import java.time.LocalDate;

/**
 * Client-side mirror of the stay-service {@code GuestLastStayResponse}.
 * Decouples the guest-service from a compile-time dependency on the
 * stay-service module.
 *
 * @param hasStays     {@code true} if the guest has at least one recorded stay
 * @param lastStayDate most recent actual check-in date, or {@code null}
 */
public record GuestLastStayClientResponse(boolean hasStays, LocalDate lastStayDate) {
}
