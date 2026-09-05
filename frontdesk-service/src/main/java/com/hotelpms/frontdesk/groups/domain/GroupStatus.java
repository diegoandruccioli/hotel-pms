package com.hotelpms.frontdesk.groups.domain;

/**
 * Lifecycle status of a {@link ReservationGroup}.
 */
public enum GroupStatus {
    PLANNED,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED
}
