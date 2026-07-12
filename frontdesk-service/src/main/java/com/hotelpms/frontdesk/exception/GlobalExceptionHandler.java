package com.hotelpms.frontdesk.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Global exception handler for the Frontdesk Service (rooms, reservations, stays).
 *
 * <p>All handlers return a {@link ProblemDetail} body (RFC 7807/9457). The
 * generic catch-all ({@link #handleGenericException}) additionally generates a
 * {@code traceId} (UUID) so the caller can correlate their error response with
 * the corresponding server-side stack trace in the application logs.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String TRACE_ID_PROPERTY = "traceId";
    private static final String ERRORS_BASE_URI = "https://hotelpms.com/errors/";
    private static final String TITLE_BAD_REQUEST = "Bad Request";
    private static final String SLUG_CONFLICT = "conflict";

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
        problemDetail.setType(errorType("not-found"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles BadRequestException.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequestException(final BadRequestException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle(TITLE_BAD_REQUEST);
        problemDetail.setType(errorType("bad-request"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ConflictException.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(final ConflictException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Conflict");
        problemDetail.setType(errorType(SLUG_CONFLICT));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ExternalServiceException — a call to guest-service or billing-service
     * failed or returned an unexpected result. Mapped to 502 Bad Gateway: the
     * frontdesk-service itself is fine, but the downstream dependency is not.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(final ExternalServiceException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                ex.getMessage());
        problemDetail.setTitle("External Service Error");
        problemDetail.setType(errorType("external-service"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles BillingNotPaidException (check-out blocked by unpaid invoice).
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(BillingNotPaidException.class)
    public ProblemDetail handleBillingNotPaidException(final BillingNotPaidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Billing Not Paid");
        problemDetail.setType(errorType("billing-not-paid"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles AlloggiatiRowLimitExceededException (export exceeds the 1 000-row
     * Portale Alloggiati Web upload limit).
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(AlloggiatiRowLimitExceededException.class)
    public ProblemDetail handleAlloggiatiRowLimitExceededException(final AlloggiatiRowLimitExceededException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setTitle("Alloggiati Row Limit Exceeded");
        problemDetail.setType(errorType("alloggiati-row-limit-exceeded"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles AlloggiatiValidationException (domain coherence violation in stay
     * guest data, e.g. FAMILIARE without CAPOFAMIGLIA).
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(AlloggiatiValidationException.class)
    public ProblemDetail handleAlloggiatiValidationException(final AlloggiatiValidationException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setTitle("Alloggiati Validation Error");
        problemDetail.setType(errorType("alloggiati-validation"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles IllegalStateException (e.g., check-out on a non-CHECKED_IN stay,
     * check-in on a reservation with an invalid status).
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(final IllegalStateException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Invalid State");
        problemDetail.setType(errorType("invalid-state"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles AccessDeniedException thrown by {@code @PreAuthorize} when the
     * caller's role is not in the allowed set. Must be explicit so the catch-all
     * handler does not swallow it and return 500 instead of 403.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(final AccessDeniedException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(errorType("access-denied"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles unreadable HTTP messages (malformed JSON payload, invalid enum value).
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(final HttpMessageNotReadableException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "INVALID_JSON_PAYLOAD");
        problemDetail.setTitle(TITLE_BAD_REQUEST);
        problemDetail.setType(errorType("invalid-json-payload"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles Bean Validation errors, attaching a per-field error map.
     *
     * @param ex the exception
     * @return the problem detail with a {@code errors} field-error map
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(final MethodArgumentNotValidException ex) {
        final String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce("", (a, b) -> a + " " + b);
        LOG.warn("[ValidationException] Details: {}", fieldErrors);

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setTitle(TITLE_BAD_REQUEST);
        problemDetail.setType(errorType("bad-request"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        final Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            final String fieldName = ((FieldError) error).getField();
            errors.put(fieldName, error.getDefaultMessage());
        });
        problemDetail.setProperty("errors", errors);
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
        LOG.warn("Data integrity violation: {}", ex.getMessage());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "RESOURCE_ALREADY_EXISTS");
        problemDetail.setTitle("Conflict: Resource Already Exists");
        problemDetail.setType(errorType(SLUG_CONFLICT));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ObjectOptimisticLockingFailureException thrown by JPA when a
     * concurrent modification is detected via a {@code @Version} field.
     *
     * <p>Returns HTTP 409 Conflict so the client knows to retry the operation.
     *
     * @param ex the optimistic locking exception
     * @return the problem detail
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(final ObjectOptimisticLockingFailureException ex) {
        LOG.warn("[OptimisticLock] Concurrent modification detected: {}", ex.getMessage());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION");
        problemDetail.setTitle("Conflict");
        problemDetail.setType(errorType(SLUG_CONFLICT));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problemDetail;
    }

    /**
     * Catch-all handler for any unhandled {@link Exception}.
     *
     * <p>Generates a unique {@code traceId} (UUID v4), logs the full stack trace
     * tagged with that ID, and includes the {@code traceId} in the response body
     * so the client can report it when contacting support.
     *
     * @param ex the unhandled exception
     * @return the problem detail containing a correlatable traceId
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        final String traceId = UUID.randomUUID().toString();
        LOG.error("[traceId={}] Unhandled exception: {}", traceId, ex.getMessage(), ex);

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(errorType("internal-server-error"));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(TRACE_ID_PROPERTY, traceId);
        return problemDetail;
    }

    @NonNull
    private static URI errorType(final String slug) {
        return Objects.requireNonNull(URI.create(ERRORS_BASE_URI + slug));
    }
}
