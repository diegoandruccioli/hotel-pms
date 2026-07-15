package com.hotelpms.notification.util;

/**
 * Masks email addresses before writing them to logs (CWE-359 PII exposure prevention).
 *
 * <p>Example: {@code alice@example.com} → {@code a***@example.com}.
 */
public final class EmailMasker {

    private EmailMasker() {
        // utility class
    }

    /**
     * Returns a partially redacted version of the email address suitable for logging.
     *
     * @param email the raw email; may be null
     * @return masked representation, or {@code "***"} when the input is null or malformed
     */
    public static String mask(final String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        final int atIndex = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
