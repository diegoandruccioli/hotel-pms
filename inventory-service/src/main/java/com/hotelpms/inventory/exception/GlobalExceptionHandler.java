package com.hotelpms.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_PROPERTY = "timestamp";

    /**
     * Handles NotFoundException.
     * 
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(final NotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/not-found")));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles validation exceptions.
     * 
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(final MethodArgumentNotValidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/bad-request")));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles data integrity violations (e.g., unique constraint).
     * 
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(final DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "RESOURCE_ALREADY_EXISTS");
        problemDetail.setTitle("Conflict: Resource Already Exists");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/conflict")));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles generic exceptions.
     * 
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        log.error("Unhandled Exception caught by GlobalExceptionHandler:", ex);
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail
                .setType(Objects.requireNonNull(URI.create("https://api.hotelpms.com/errors/internal-server-error")));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }
}
