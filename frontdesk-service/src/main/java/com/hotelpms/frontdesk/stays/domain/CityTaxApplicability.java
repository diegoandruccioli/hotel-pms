package com.hotelpms.frontdesk.stays.domain;

/**
 * Whether a hotel's comune levies a tourist tax at all — distinct from
 * whether a rate is currently configured. Lets a hotel in a comune with no
 * tourist tax silence the "not configured" warnings for good, without that
 * silence also hiding a hotel that simply forgot to configure a real rate.
 */
public enum CityTaxApplicability {

    /** Not yet declared either way — still eligible for not-configured warnings. Default. */
    UNKNOWN,

    /** The hotel has declared its comune does not levy a tourist tax. Silences the warnings. */
    NOT_APPLICABLE,

    /** Declared applicable. Informational — a configured rate is still required for assessment. */
    APPLICABLE
}
