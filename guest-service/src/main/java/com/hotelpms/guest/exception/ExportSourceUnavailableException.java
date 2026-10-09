package com.hotelpms.guest.exception;

/**
 * Thrown when a downstream source of the GDPR Art. 15/20 export (stay or invoice history)
 * cannot be read. The export fails instead of silently returning an incomplete file,
 * which the data subject could not tell apart from "no stays / no invoices".
 */
public class ExportSourceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String section;

    /**
     * Constructs a new ExportSourceUnavailableException.
     *
     * @param section the export section that could not be read (e.g. {@code stays}, {@code invoices})
     * @param cause   the downstream failure
     */
    public ExportSourceUnavailableException(final String section, final Throwable cause) {
        super("EXPORT_SOURCE_UNAVAILABLE: " + section, cause);
        this.section = section;
    }

    /**
     * Returns the export section that could not be read.
     *
     * @return the section name
     */
    public String getSection() {
        return section;
    }
}
