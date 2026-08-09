package com.hotelpms.frontdesk.quotations.domain;

/**
 * Quotation lifecycle status.
 *
 * <p>{@code DRAFT}/{@code SENT}/{@code ACCEPTED}/{@code DECLINED} are the only
 * values ever written to the {@code quotations.status} column (also enforced
 * by the DB-level {@code chk_quotations_status} constraint, V10).
 * {@code EXPIRED} is response-only: computed at read time from
 * {@code validUntil} (see {@code Quotation#isExpired}) rather than written by
 * a scheduled job — YAGNI, no cron needed until a real requirement for
 * time-based side effects (e.g. an expiry email) shows up.
 */
public enum QuotationStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
