package com.hotelpms.frontdesk.reservations.domain;

/**
 * Reservation lifecycle status.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    PARTIALLY_CHECKED_IN,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW
}
