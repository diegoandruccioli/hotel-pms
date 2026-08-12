package com.hotelpms.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the notification-service-specific handler, plus a check
 * that bean-validation errors now come out in the shared RFC 7807 shape
 * (via the inherited {@code AbstractProblemDetailAdvice}) instead of this
 * service's old bespoke single-string {@code detail} format.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesIllegalStateExceptionAs500NotificationFailure() {
        final ProblemDetail result = handler.handleMailFailure(new IllegalStateException("SMTP timeout"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getTitle()).isEqualTo("Notification Failure");
        assertThat(result.getDetail()).isEqualTo("Email could not be dispatched");
    }

    @Test
    void inheritsSharedValidationErrorShapeInsteadOfOwnBespokeFormat() throws NoSuchMethodException {
        final BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "recipient", "must not be blank"));
        final MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameterStub(), bindException.getBindingResult());

        final ProblemDetail result = handler.handleValidationExceptions(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/bad-request");
        assertThat(result.getProperties()).containsKey("errors");
    }

    private static final class MethodParameterStub extends MethodParameter {
        MethodParameterStub() throws NoSuchMethodException {
            super(GlobalExceptionHandlerTest.class.getDeclaredMethod(
                    "inheritsSharedValidationErrorShapeInsteadOfOwnBespokeFormat"), -1);
        }
    }
}
