package com.hotelpms.stay.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Global exception handler for the Stay Service.
 * Translates exceptions into RFC 7807 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles NotFoundException.
     *
     * @param ex the exception
     * @return the ProblemDetail response
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(final NotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/not-found")));
        problemDetail.setTitle("Resource Not Found");
        return problemDetail;
    }

    /**
     * Handles ExternalServiceException.
     *
     * @param ex the exception
     * @return the ProblemDetail response
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(final ExternalServiceException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problemDetail
                .setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/external-service-failure")));
        problemDetail.setTitle("External Service Failure");
        return problemDetail;
    }

    /**
     * Handles BillingNotPaidException (check-out blocked by unpaid invoice).
     *
     * @param ex the exception
     * @return the ProblemDetail response
     */
    @ExceptionHandler(BillingNotPaidException.class)
    public ProblemDetail handleBillingNotPaidException(final BillingNotPaidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/billing-not-paid")));
        problemDetail.setTitle("Billing Not Paid");
        return problemDetail;
    }

    /**
     * Handles IllegalStateException (e.g., check-out on a non-CHECKED_IN stay).
     *
     * @param ex the exception
     * @return the ProblemDetail response
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(final IllegalStateException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/invalid-state")));
        problemDetail.setTitle("Invalid State");
        return problemDetail;
    }

    /**
     * Handles validation errors (MethodArgumentNotValidException).
     *
     * @param ex the exception
     * @return the ProblemDetail response containing validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(final MethodArgumentNotValidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/validation-failed")));
        problemDetail.setTitle("Bad Request");

        final Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            final String fieldName = ((FieldError) error).getField();
            final String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    /**
     * Handles unreadable messages (e.g., malformed JSON payload, invalid enum values).
     *
     * @param ex the exception
     * @return the ProblemDetail response
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(
            final org.springframework.http.converter.HttpMessageNotReadableException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "INVALID_JSON_PAYLOAD");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/invalid-json-payload")));
        problemDetail.setTitle("Bad Request");
        return problemDetail;
    }

    /**
     * Handles generic Exception.
     *
     * @param ex the exception
     * @return the ProblemDetail response
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/internal-server-error")));
        problemDetail.setTitle("Internal Server Error");
        return problemDetail;
    }
}
