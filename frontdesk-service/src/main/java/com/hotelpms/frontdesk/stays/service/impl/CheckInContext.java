package com.hotelpms.frontdesk.stays.service.impl;

import java.time.LocalDate;

/**
 * Holds the validated check-out date plus denormalized display info captured
 * during check-in validation (avoids redundant lookups).
 *
 * @param checkOutDate     expected check-out date from the reservation (may be null for walk-ins)
 * @param guestDisplayName primary guest "Cognome Nome" for UI display
 * @param roomNumber       room number for UI display
 */
record CheckInContext(LocalDate checkOutDate, String guestDisplayName, String roomNumber) {
}
