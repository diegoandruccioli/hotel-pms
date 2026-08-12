package com.hotelpms.notification.exception;

import com.hotelpms.commonweb.exception.AbstractProblemDetailAdvice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Objects;

/**
 * Exception handling specific to Notification Service. Shared handlers
 * (malformed bodies, bean validation, {@code @PreAuthorize} denials, Feign
 * failures, the generic 500 catch-all) live in {@link AbstractProblemDetailAdvice}.
 *
 * <p>Bean-validation errors ({@code MethodArgumentNotValidException}) used to
 * have a bespoke shape here (a single concatenated {@code detail} string,
 * different {@code type}/{@code title}) — now inherited from the base for the
 * same RFC 7807 shape every other service returns.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends AbstractProblemDetailAdvice {

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
        problem.setType(Objects.requireNonNull(URI.create("https://hotel-pms.com/errors/notification-failure")));
        problem.setTitle("Notification Failure");
        problem.setDetail("Email could not be dispatched");
        return problem;
    }
}
