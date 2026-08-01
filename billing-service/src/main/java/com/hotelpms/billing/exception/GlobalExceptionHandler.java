package com.hotelpms.billing.exception;

import com.hotelpms.commonweb.exception.AbstractProblemDetailAdvice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Exception handling specific to Billing Service. Shared handlers (malformed
 * bodies, bean validation, Feign failures, {@code @PreAuthorize} denials, the
 * generic 500 catch-all) live in {@link AbstractProblemDetailAdvice}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractProblemDetailAdvice {

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
        problemDetail.setType(errorType("not-found"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles InvoiceConflictException.
     *
     * @param ex the exception
     * @return ProblemDetail with 409 status
     */
    @ExceptionHandler(InvoiceConflictException.class)
    public ProblemDetail handleInvoiceConflictException(final InvoiceConflictException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Invoice Conflict");
        problemDetail.setType(errorType("conflict"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles BillingValidationException.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(BillingValidationException.class)
    public ProblemDetail handleBillingValidationException(final BillingValidationException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Billing Validation Error");
        problemDetail.setType(errorType("validation-error"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ExternalServiceException.
     *
     * @param ex the exception
     * @return ProblemDetail with 502 status
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(final ExternalServiceException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                ex.getMessage());
        problemDetail.setTitle("External Service Error");
        problemDetail.setType(errorType("external-service-error"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles ObjectOptimisticLockingFailureException thrown by JPA when a concurrent
     * modification is detected via the {@code @Version} field on {@link com.hotelpms.billing.domain.Invoice}
     * (e.g. billing-service and F&amp;B both adding a charge to the same invoice at once).
     *
     * @param ex the optimistic locking exception
     * @return ProblemDetail with 409 status
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(final ObjectOptimisticLockingFailureException ex) {
        log.warn("[OptimisticLock] Concurrent modification detected: {}", ex.getMessage());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION");
        problemDetail.setTitle("Conflict");
        problemDetail.setType(errorType("conflict"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }
}
