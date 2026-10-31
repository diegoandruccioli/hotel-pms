package com.hotelpms.internalauth.security;

/**
 * Thrown by a {@link NonceStore} when its backing store cannot be reached or does not answer in time, so the
 * replay check (T-GW-08) could not be performed at all.
 *
 * <p>Not the same as a replay: {@link NonceStore#claim} returns {@code false} for that. {@link
 * InternalAuthFilter} turns this into a fail-closed 503 — the request is never admitted without the check,
 * and the caller can tell an outage from a rejected signature.
 */
public class NonceStoreUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what could not be done
     * @param cause   the underlying failure of the store
     */
    public NonceStoreUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
