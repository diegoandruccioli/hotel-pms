package com.hotelpms.billing.domain;

/**
 * Whether an invoice is tied to a single stay (the default) or is a MASTER
 * folio for a reservation group in frontdesk-service.
 */
public enum FolioType {
    INDIVIDUAL,
    MASTER
}
