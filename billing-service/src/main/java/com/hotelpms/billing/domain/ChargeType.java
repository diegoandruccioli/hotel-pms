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
    EXTRA,

    /**
     * Municipal tourist tax (imposta di soggiorno, art. 4 D.Lgs. 23/2011) — collected
     * by the operator in the comune's name, out of VAT scope (see
     * {@code InvoiceServiceImpl.vatTreatmentFor}, FatturaPA {@code Natura} code N1).
     */
    CITY_TAX
}
