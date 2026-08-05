package com.hotelpms.guest.exception;

import com.hotelpms.commonweb.exception.AbstractProblemDetailAdvice;
import com.hotelpms.guest.dto.response.GdprLegalHoldResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Exception handling specific to Guest Service. Shared handlers (malformed
 * bodies, bean validation, Feign failures, {@code @PreAuthorize} denials, the
 * generic 500 catch-all) live in {@link AbstractProblemDetailAdvice}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractProblemDetailAdvice {

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
        problemDetail.setType(errorType("not-found"));
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
        problemDetail.setTitle("Request Validation Error");
        problemDetail.setType(errorType("bad-request"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles GuestConflictException — an operation conflicts with the guest's
     * current state (e.g. deleting a guest with active reservations).
     *
     * @param ex the exception
     * @return ProblemDetail with 409 status
     */
    @ExceptionHandler(GuestConflictException.class)
    public ProblemDetail handleGuestConflictException(final GuestConflictException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Guest Conflict");
        problemDetail.setType(errorType("conflict"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }
}
