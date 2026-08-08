package com.hotelpms.frontdesk.reservations.dto;

import java.math.BigDecimal;

/**
 * The snapshotted price and night count for one room of a reservation,
 * both derived from the same source — the reservation's own {@code
 * checkInDate}/{@code checkOutDate} — so a check-in charge built from this
 * never shows a night count inconsistent with the amount actually billed.
 *
 * @param price  the total price snapshotted on the reservation's line item at booking time
 * @param nights the number of nights the reservation covers ({@code checkOutDate - checkInDate}, minimum 1)
 */
public record ReservedRoomCharge(BigDecimal price, int nights) {
}
