package com.hotelpms.guest.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Global exception handler providing RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_FIELD = "timestamp";

    /**
     * Handles NotFoundException.
     *
     * @param ex the exception
     * @return ProblemDetail with 404 status
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(final NotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotel-pms.com/errors/not-found")));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ExternalServiceException or FeignException.
     *
     * @param ex the exception
     * @return ProblemDetail with 502 status
     */
    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleExternalServiceException(final Exception ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                ex.getMessage());
        problemDetail.setTitle("External Service Error");
        problemDetail
                .setType(Objects.requireNonNull(URI.create("https://hotel-pms.com/errors/external-service-error")));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles MethodArgumentNotValidException for DTO validation.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status and field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(final MethodArgumentNotValidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setTitle("Request Validation Error");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotel-pms.com/errors/bad-request")));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());

        final List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * Handles generic exceptions.
     *
     * @param ex the exception
     * @return ProblemDetail with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotel-pms.com/errors/internal-server-error")));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }
}
