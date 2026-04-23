package com.hotelpms.billing.domain;

/**
 * Enumeration of billable charge categories on an invoice.
 */
public enum ChargeType {

    /** Nightly room rate charge added at check-in or per night. */
    ROOM_NIGHT,

    /** Food and Beverage order billed to the room. */
    FB_ORDER,

    /** Miscellaneous extra charge (laundry, parking, minibar, etc.). */
    EXTRA
}
