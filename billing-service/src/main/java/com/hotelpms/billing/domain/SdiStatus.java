package com.hotelpms.billing.domain;

/**
 * Tracks the SDI (Sistema di Interscambio) transmission lifecycle for a FATTURA.
 * Only invoices with documentType=FATTURA are eligible for SDI submission.
 */
public enum SdiStatus {
    /** Invoice has not yet been submitted to SDI. */
    NOT_SENT,
    /** XML has been generated and uploaded to SDI by the operator. */
    SENT,
    /** SDI has confirmed acceptance of the invoice. */
    ACCEPTED,
    /** SDI has rejected the invoice; operator must correct and resubmit. */
    REJECTED
}
