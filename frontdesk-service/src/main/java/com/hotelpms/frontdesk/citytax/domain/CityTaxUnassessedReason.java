package com.hotelpms.frontdesk.citytax.domain;

/**
 * Why a {@link CityTaxAssessment} has no tax computed ({@code totalAmount} is
 * always {@code 0} in this case) — {@code null} on the entity itself means the
 * tax <em>was</em> computed (possibly to zero because every guest was exempt).
 */
public enum CityTaxUnassessedReason {

    /** The hotel has not configured its comune ({@code HotelSettings.comuneCodice}). */
    COMUNE_NOT_CONFIGURED,

    /** No {@code HotelCategoryHistory} entry covers the stay's check-in date. */
    CATEGORY_NOT_RECORDED,

    /** No {@code CityTaxRate} covers the (comune, category, date) combination. */
    NO_RATE_FOR_DATE,

    /** The hotel has declared its comune does not levy a tourist tax at all — never a gap. */
    NOT_APPLICABLE
}
