package com.hotelpms.guest.util;

/**
 * Constants for validation.
 */
public final class ValidationConstants {

    public static final int MAX_FIRST_NAME_LENGTH = 100;
    public static final int MAX_LAST_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 150;
    public static final int MAX_PHONE_LENGTH = 20;
    public static final int MAX_ADDRESS_LENGTH = 255;
    public static final int MAX_LOCATION_LENGTH = 50;
    public static final int MAX_DOCUMENT_NUMBER_LENGTH = 100;
    public static final int MAX_COUNTRY_LENGTH = 100;

    /** Unicode letters, spaces, hyphens, apostrophes — covers international names. */
    public static final String NAME_PATTERN = "^[\\p{L} '\\-]+$";

    /**
     * Optional leading +, then digits, spaces, parentheses and hyphens.
     * Field itself is optional, so empty string must also match.
     */
    public static final String PHONE_PATTERN = "^$|^[+]?[0-9 ()\\-]+$";

    /**
     * Blocks HTML/script injection characters (&lt; &gt; &amp; &quot;).
     * Field itself is optional, so empty string must also match.
     */
    public static final String TEXT_SAFE_PATTERN = "^$|^[^<>&\"]+$";

    /**
     * Letters, digits, spaces, hyphens, apostrophes and dots (place names).
     * Field itself is optional, so empty string must also match.
     */
    public static final String LOCATION_PATTERN = "^$|^[\\p{L}0-9 '\\-.]+$";

    /** Alphanumeric and hyphens only — standard document number format. */
    public static final String DOCUMENT_NUMBER_PATTERN = "^[A-Za-z0-9\\-]+$";

    private ValidationConstants() {
        // Private constructor to prevent instantiation
    }
}
