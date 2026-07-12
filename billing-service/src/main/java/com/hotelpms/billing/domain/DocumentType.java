package com.hotelpms.billing.domain;

/**
 * Distinguishes fiscal invoices (subject to VAT rules) from
 * non-fiscal receipts (no VAT breakdown required on the document).
 */
public enum DocumentType {
    FATTURA,
    RICEVUTA
}
