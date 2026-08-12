package com.hotelpms.frontdesk.exception;

import com.hotelpms.commonweb.exception.AbstractProblemDetailAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

/**
 * Exception handling specific to Frontdesk Service (rooms, reservations,
 * stays). Shared handlers (malformed bodies, bean validation, {@code
 * @PreAuthorize} denials, downstream Feign failures, the generic Spring
 * native 404/400/405/415 handlers) live in {@link AbstractProblemDetailAdvice}.
 *
 * <p>{@link #handleGenericException} is the one override: this service
 * additionally generates a {@code traceId} on the generic 500 path so callers
 * can correlate their error response with the corresponding server-side
 * stack trace — no other service does this, so it cannot live in the shared
 * base without adding it everywhere.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractProblemDetailAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TRACE_ID_PROPERTY = "traceId";
    private static final String TITLE_BAD_REQUEST = "Bad Request";
    private static final String SLUG_CONFLICT = "conflict";
    private static final String SLUG_BAD_REQUEST = "bad-request";
    /** PostgreSQL SQLState for a NOT NULL constraint violation. */
    private static final String SQLSTATE_NOT_NULL_VIOLATION = "23502";

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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setType(errorType(SLUG_BAD_REQUEST));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    // AccessDeniedException (403, @PreAuthorize denials) and FeignException (502,
    // downstream guest-service/billing-service/notification-service failures) are
    // handled by the inherited AbstractProblemDetailAdvice — this service used to
    // duplicate both locally with identical behavior; removed now that this class
    // extends the base.

    /**
     * Handles data integrity violations (unique constraint, not-null constraint, ...).
     *
     * <p>A NOT NULL violation is a missing-required-field bug, not a
     * conflict — reporting it as 409 "RESOURCE_ALREADY_EXISTS" is actively
     * misleading (nothing "already exists"; some other layer, typically
     * frontend validation, failed to require a field the database does).
     * Distinguished by SQLState (23502) rather than message-sniffing, which
     * is fragile across drivers/locales.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(final DataIntegrityViolationException ex) {
        if (isNotNullViolation(ex)) {
            LOG.warn("Not-null constraint violation (likely a validation gap upstream): {}", ex.getMessage());
            final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "REQUIRED_FIELD_MISSING");
            problemDetail.setTitle(TITLE_BAD_REQUEST);
            problemDetail.setType(errorType(SLUG_BAD_REQUEST));
            problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
            return problemDetail;
        }

        LOG.warn("Data integrity violation: {}", ex.getMessage());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "RESOURCE_ALREADY_EXISTS");
        problemDetail.setTitle("Conflict: Resource Already Exists");
        problemDetail.setType(errorType(SLUG_CONFLICT));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    private static boolean isNotNullViolation(final DataIntegrityViolationException ex) {
        final Throwable cause = ex.getMostSpecificCause();
        return cause instanceof final java.sql.SQLException sqlException
                && SQLSTATE_NOT_NULL_VIOLATION.equals(sqlException.getSQLState());
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
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Overrides the base catch-all to additionally generate a unique {@code
     * traceId} (UUID v4), log the full stack trace tagged with that ID, and
     * include the {@code traceId} in the response body so the client can
     * report it when contacting support. No other service in the project
     * does this, so it cannot be hoisted into {@link AbstractProblemDetailAdvice}
     * without adding it everywhere.
     *
     * @param ex the unhandled exception
     * @return the problem detail containing a correlatable traceId
     */
    @Override
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        final String traceId = UUID.randomUUID().toString();
        LOG.error("[traceId={}] Unhandled exception: {}", traceId, ex.getMessage(), ex);

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(errorType("internal-server-error"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        problemDetail.setProperty(TRACE_ID_PROPERTY, traceId);
        return problemDetail;
    }
}
