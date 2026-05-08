package com.hotelpms.guest.exception;

import java.time.LocalDate;

/**
 * Thrown when a GDPR hard-delete request is blocked by an active legal hold.
 *
 * <p>Maps to HTTP 451 Unavailable For Legal Reasons (RFC 7725).
 * The exception carries the earliest date at which deletion will become legally
 * permissible, so the caller can communicate it to the end user.
 *
 * <p>Legal holds recognised by Italian law that may block deletion:
 * <ul>
 *   <li><strong>TULPS</strong> – Testo Unico delle Leggi di Pubblica Sicurezza:
 *       5-year minimum for Alloggiati Web data.</li>
 *   <li><strong>FISCAL</strong> – Codice Civile art. 2220:
 *       10-year minimum for accounting documents.</li>
 * </ul>
 */
public class GdprLegalHoldException extends RuntimeException {

    /** Identifies which legal obligation is blocking the deletion. */
    public enum LegalBasis { TULPS, FISCAL, TULPS_AND_FISCAL }

    private final LocalDate unlocksAt;
    private final LegalBasis legalBasis;

    /**
     * Constructs the exception.
     *
     * @param message    human-readable reason
     * @param unlocksAt  earliest date at which deletion becomes permissible
     * @param legalBasis which legal obligation is blocking deletion
     */
    public GdprLegalHoldException(final String message,
                                   final LocalDate unlocksAt,
                                   final LegalBasis legalBasis) {
        super(message);
        this.unlocksAt  = unlocksAt;
        this.legalBasis = legalBasis;
    }

    /**
     * Returns the earliest date at which the legal hold expires.
     *
     * @return the unlock date
     */
    public LocalDate getUnlocksAt() {
        return unlocksAt;
    }

    /**
     * Returns which legal obligation is blocking the deletion.
     *
     * @return the legal basis for the hold
     */
    public LegalBasis getLegalBasis() {
        return legalBasis;
    }
}
