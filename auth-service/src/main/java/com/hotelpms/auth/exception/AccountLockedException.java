package com.hotelpms.auth.exception;

/**
 * Exception thrown when a user account is temporarily locked
 * after too many consecutive failed login attempts (T-AUTH-02).
 */
public class AccountLockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new AccountLockedException with the specified message.
     *
     * @param message the detail message
     */
    public AccountLockedException(final String message) {
        super(message);
    }
}
