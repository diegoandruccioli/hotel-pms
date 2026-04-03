package com.hotelpms.reservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a client sends a request that cannot be processed
 * due to invalid or missing input data.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new BadRequestException with the given detail message.
     *
     * @param message the detail message
     */
    public BadRequestException(final String message) {
        super(message);
    }

    /**
     * Constructs a new BadRequestException with the given detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public BadRequestException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
