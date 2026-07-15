package com.hotelpms.notification.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Objects;

/**
 * Centralized error handling — maps exceptions to RFC 7807 problem detail responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures on request bodies.
     *
     * @param ex the validation exception
     * @return a 400 problem detail listing all constraint violations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(final MethodArgumentNotValidException ex) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(Objects.requireNonNull(URI.create("https://hotelpms.local/problems/validation-error")));
        problem.setTitle("Validation Error");
        problem.setDetail(ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b));
        return problem;
    }

    /**
     * Handles SMTP send failures and other unexpected errors.
     *
     * @param ex the exception
     * @return a 500 problem detail
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleMailFailure(final IllegalStateException ex) {
        log.error("[NOTIFY] Email send failure: {}", ex.getMessage());
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(Objects.requireNonNull(URI.create("https://hotelpms.local/problems/notification-failure")));
        problem.setTitle("Notification Failure");
        problem.setDetail("Email could not be dispatched");
        return problem;
    }
}
