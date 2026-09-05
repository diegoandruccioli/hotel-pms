package com.hotelpms.frontdesk.nightaudit.domain;

/**
 * Lifecycle of a single {@link NightAuditRun}.
 */
public enum NightAuditStatus {

    /** The run is in progress — the claim row has been inserted, work hasn't finished yet. */
    RUNNING,

    /** The run finished successfully; the row is now immutable. */
    COMPLETED,

    /** The run threw before finishing; the row may be retried (deleted and re-created). */
    FAILED
}
