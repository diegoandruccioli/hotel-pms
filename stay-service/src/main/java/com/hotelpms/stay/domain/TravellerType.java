package com.hotelpms.stay.domain;

/**
 * Traveller type classification for Alloggiati Web.
 * Numeric codes match the official Portale Alloggiati Web TIPALLOG table.
 */
public enum TravellerType {

    OSPITE_SINGOLO(16),
    CAPOFAMIGLIA(17),
    CAPOGRUPPO(18),
    FAMILIARE(19),
    MEMBRO_GRUPPO(20);

    private final int numericCode;

    TravellerType(final int numericCode) {
        this.numericCode = numericCode;
    }

    /**
     * Returns the 2-digit portal code (e.g. {@code "16"} for OSPITE_SINGOLO).
     *
     * @return the zero-padded 2-digit TIPALLOG string code
     */
    public String portalCode() {
        return String.format("%02d", numericCode);
    }

    /**
     * Returns the numeric TIPALLOG code used for record ordering in the tracciato.
     *
     * @return the numeric code (16–20)
     */
    public int numericCode() {
        return numericCode;
    }
}

