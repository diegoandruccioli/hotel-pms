package com.hotelpms.pdftemplate;

/**
 * Thrown when template processing or PDF rendering fails.
 */
public class PdfRenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message and cause.
     *
     * @param message description of the failure
     * @param cause   the underlying exception
     */
    public PdfRenderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
