package com.hotelpms.reservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a request conflicts with the current state of the resource,
 * for example when optimistic locking detects a concurrent modification.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ConflictException with the given detail message.
     *
     * @param message the detail message
     */
    public ConflictException(final String message) {
        super(message);
    }

    /**
     * Constructs a new ConflictException with the given detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConflictException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
