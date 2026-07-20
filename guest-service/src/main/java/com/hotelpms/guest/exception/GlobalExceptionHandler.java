package com.hotelpms.guest.exception;

import com.hotelpms.guest.dto.response.GdprLegalHoldResponse;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.lang.NonNull;
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
    private static final String REQUEST_VALIDATION_ERROR_TITLE = "Request Validation Error";
    private static final String BAD_REQUEST_ERROR_TYPE = "https://hotel-pms.com/errors/bad-request";

    /**
     * Handles GdprLegalHoldException — returns HTTP 451 Unavailable For Legal
     * Reasons (RFC 7725) with a structured body indicating the unlock date and
     * which legal obligation is blocking the deletion (T-GST-05).
     *
     * @param ex the exception
     * @return 451 response with GdprLegalHoldResponse body
     */
    @ExceptionHandler(GdprLegalHoldException.class)
    public ResponseEntity<GdprLegalHoldResponse> handleGdprLegalHold(
            final GdprLegalHoldException ex) {
        final GdprLegalHoldResponse body = new GdprLegalHoldResponse(
                "LEGAL_HOLD_ACTIVE",
                ex.getMessage(),
                ex.getUnlocksAt(),
                ex.getLegalBasis().name());
        return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS.value())).body(body);
    }

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
     * Handles GuestValidationException — a business rule violation not expressible
     * as a Jakarta Bean Validation annotation (e.g. an invalid Comune/Provincia pair).
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(GuestValidationException.class)
    public ProblemDetail handleGuestValidationException(final GuestValidationException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle(REQUEST_VALIDATION_ERROR_TITLE);
        problemDetail.setType(Objects.requireNonNull(URI.create(BAD_REQUEST_ERROR_TYPE)));
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
        problemDetail.setTitle(REQUEST_VALIDATION_ERROR_TITLE);
        problemDetail.setType(Objects.requireNonNull(URI.create(BAD_REQUEST_ERROR_TYPE)));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());

        final List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map((@NonNull FieldError fe) -> fe.getDefaultMessage())
                .collect(Collectors.toList());
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * Handles malformed request bodies (invalid JSON, unparseable enum values, etc.).
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(final HttpMessageNotReadableException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "INVALID_JSON_PAYLOAD");
        problemDetail.setTitle(REQUEST_VALIDATION_ERROR_TITLE);
        problemDetail.setType(Objects.requireNonNull(URI.create(BAD_REQUEST_ERROR_TYPE)));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
