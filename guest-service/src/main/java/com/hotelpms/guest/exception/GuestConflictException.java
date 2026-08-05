package com.hotelpms.guest.exception;

/**
 * Thrown when an operation conflicts with the current state of a guest
 * (e.g. deleting a guest that still has active reservations).
 */
public class GuestConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new GuestConflictException with the given detail message.
     *
     * @param message the detail message
     */
    public GuestConflictException(final String message) {
        super(message);
    }
}
