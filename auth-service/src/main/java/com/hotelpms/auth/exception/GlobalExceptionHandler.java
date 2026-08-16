package com.hotelpms.auth.exception;

import com.hotelpms.commonweb.exception.AbstractProblemDetailAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Exception handling specific to Auth Service. Shared handlers (malformed
 * bodies, bean validation, Feign failures, {@code @PreAuthorize} denials,
 * the generic 500 catch-all) live in {@link AbstractProblemDetailAdvice}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractProblemDetailAdvice {

    /**
     * Handles AccountLockedException.
     *
     * <p>Finding #8 (security-report.md, MEDIUM): previously mapped to 429 with a
     * distinct "Account Temporarily Locked" body — that let an attacker confirm a
     * username exists once it accumulated enough failed attempts to lock,
     * reintroducing T-AUTH-01 user enumeration at the HTTP-status/body level. Now
     * returns byte-for-byte the same response as {@link BadCredentialsException};
     * the real reason is only visible server-side via the {@code ACCOUNT_LOCKED}
     * log line at the throw site in {@code AuthServiceImpl.login()}.
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLockedException(final AccountLockedException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        problemDetail.setTitle("Authentication Failed");
        problemDetail.setType(errorType("unauthorized"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles BadCredentialsException.
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentialsException(final BadCredentialsException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problemDetail.setTitle("Authentication Failed");
        problemDetail.setType(errorType("unauthorized"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles DuplicateResourceException.
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResourceException(final DuplicateResourceException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Resource Conflict");
        problemDetail.setType(errorType("conflict"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles NotFoundException (HTTP 404).
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(final NotFoundException ex) {
        final ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Not Found");
        detail.setType(errorType("not-found"));
        detail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return detail;
    }

    /**
     * Handles IllegalStateException (e.g. self-deactivation).
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(final IllegalStateException ex) {
        final ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Conflict");
        detail.setType(errorType("conflict"));
        detail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return detail;
    }
}
