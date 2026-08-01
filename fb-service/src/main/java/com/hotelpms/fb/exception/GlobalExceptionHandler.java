package com.hotelpms.fb.exception;

import com.hotelpms.commonweb.exception.AbstractProblemDetailAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Exception handling specific to the Food &amp; Beverage Service. Shared
 * handlers (malformed bodies, bean validation, Feign failures,
 * {@code @PreAuthorize} denials, the generic 500 catch-all) live in
 * {@link AbstractProblemDetailAdvice}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractProblemDetailAdvice {

    private static final String URI_NOT_FOUND = "https://hotel-pms.com/errors/not-found";

    /**
     * Handles StayNotFoundException.
     *
     * @param ex the exception
     * @return ProblemDetail with 404 status
     */
    @ExceptionHandler(StayNotFoundException.class)
    public ProblemDetail handleStayNotFoundException(final StayNotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Stay Not Found");
        problemDetail.setType(Objects.requireNonNull(URI.create(URI_NOT_FOUND)));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles MenuItemNotFoundException.
     *
     * @param ex the exception
     * @return ProblemDetail with 404 status
     */
    @ExceptionHandler(MenuItemNotFoundException.class)
    public ProblemDetail handleMenuItemNotFoundException(final MenuItemNotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Menu Item Not Found");
        problemDetail.setType(Objects.requireNonNull(URI.create(URI_NOT_FOUND)));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles OrderNotFoundException.
     *
     * @param ex the exception
     * @return ProblemDetail with 404 status
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFoundException(final OrderNotFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Order Not Found");
        problemDetail.setType(Objects.requireNonNull(URI.create(URI_NOT_FOUND)));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles OrderValidationException.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(OrderValidationException.class)
    public ProblemDetail handleOrderValidationException(final OrderValidationException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Order Validation Error");
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
}
