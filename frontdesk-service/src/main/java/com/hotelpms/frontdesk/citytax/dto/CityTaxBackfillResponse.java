package com.hotelpms.frontdesk.citytax.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a tourist-tax backfill pass — either a dry-run preview (nothing
 * written, nothing charged) or the outcome of actually posting the charges.
 *
 * @param lines        one entry per affected stay
 * @param totalAmount  sum of every line's {@code amount}, charged or not
 * @param chargedCount how many lines were actually charged ({@code 0} on preview)
 * @param skippedCount how many lines were left uncharged (closed invoice or still unconfigured)
 */
public record CityTaxBackfillResponse(
        List<CityTaxBackfillLineResponse> lines,
        BigDecimal totalAmount,
        int chargedCount,
        int skippedCount) {

    /** Defensive copy of the mutable list. */
    public CityTaxBackfillResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /**
     * Returns a defensive copy of the lines.
     *
     * @return the backfill lines
     */
    @Override
    public List<CityTaxBackfillLineResponse> lines() {
        return List.copyOf(lines);
    }
}
