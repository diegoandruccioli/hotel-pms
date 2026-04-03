package com.hotelpms.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Centralized exception handler for throwing standard Problem Details (RFC
 * 7807).
 */
@ControllerAdvice
@lombok.extern.slf4j.Slf4j
public class GlobalExceptionHandler {

    private static final String TIMESTAMP = "timestamp";

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
        problemDetail.setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/unauthorized")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
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
        problemDetail.setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/conflict")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }

    /**
     * Handles MethodArgumentNotValidException.
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(final MethodArgumentNotValidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/bad-request")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        final Map<String, String> errors = new HashMap<>();
        for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * Handles generic Exception.
     *
     * @param ex the exception
     * @return the problem detail mapping
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        log.error("Unhandled Exception caught: ", ex);
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail
                .setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/internal-server-error")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }
}
