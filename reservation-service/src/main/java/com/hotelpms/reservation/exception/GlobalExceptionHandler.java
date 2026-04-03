package com.hotelpms.reservation.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Global exception handler providing RFC 7807 Problem Details.
 *
 * <p>
 * All handlers return a {@link ProblemDetail} body. The generic catch-all
 * ({@link #handleGenericException}) additionally generates a {@code traceId}
 * (UUID) so that the caller can correlate their error response with the
 * corresponding server-side stack trace in the application logs.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TIMESTAMP = "timestamp";
    private static final String TRACE_ID = "traceId";

    /**
     * Handles NotFoundException.
     *
     * @param ex the exception
     * @return the problem detail response
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(final NotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/not-found")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ExternalServiceException.
     *
     * @param ex the exception
     * @return the problem detail response
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(final ExternalServiceException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage());
        problemDetail.setTitle("External Service Error");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/external-service")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }

    /**
     * Handles MethodArgumentNotValidException (Validation).
     *
     * @param ex the exception
     * @return the problem detail response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(final MethodArgumentNotValidException ex) {
        final String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce("", (a, b) -> a + " " + b);
        LOG.warn("[ValidationException] Details: {}", fieldErrors);
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/validation-failed")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }

    /**
     * Catch-all handler for any unhandled {@link Exception}.
     *
     * <p>
     * Generates a unique {@code traceId} (UUID v4), logs the full stack trace
     * tagged with that ID, and includes the {@code traceId} in the response body so
     * that the client can report it when contacting support.
     *
     * @param ex the unhandled exception
     * @return the problem detail response containing a correlatable traceId
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        final String traceId = UUID.randomUUID().toString();
        LOG.error("[traceId={}] Unhandled exception: {}", traceId, ex.getMessage(), ex);

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/internal-error")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        problemDetail.setProperty(TRACE_ID, traceId);
        return problemDetail;
    }

    /**
     * Handles BadRequestException.
     *
     * @param ex the exception
     * @return the problem detail response
     */
    @ExceptionHandler(BadRequestException.class)
    public final ProblemDetail handleBadRequestException(final BadRequestException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(Objects.requireNonNull(URI.create("https://hotelpms.com/errors/bad-request")));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }
}
