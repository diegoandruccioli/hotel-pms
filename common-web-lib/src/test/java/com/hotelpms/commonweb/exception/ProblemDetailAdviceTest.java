package com.hotelpms.commonweb.exception;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbstractProblemDetailAdvice} via a trivial concrete
 * subclass, mirroring how billing-service/fb-service/guest-service's own
 * {@code GlobalExceptionHandler} extends it in production.
 */
class ProblemDetailAdviceTest {

    private final TestAdvice advice = new TestAdvice();

    @Test
    @SuppressWarnings("deprecation")
    void handlesHttpMessageNotReadableExceptionAs400() {
        final HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json");

        final ProblemDetail result = advice.handleHttpMessageNotReadableException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Request Validation Error");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/bad-request");
        assertThat(result.getDetail()).isEqualTo("INVALID_JSON_PAYLOAD");
        assertThat(result.getProperties()).containsKey("timestamp");
    }

    @Test
    void handlesValidationExceptionAs400WithFieldMessages() throws NoSuchMethodException {
        final BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "name", "must not be blank"));
        final MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameterStub(), bindException.getBindingResult());

        final ProblemDetail result = advice.handleValidationExceptions(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/bad-request");
        final Object errorsProperty = Objects.requireNonNull(result.getProperties()).get("errors");
        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) Objects.requireNonNull(errorsProperty);
        assertThat(errors).containsExactly("must not be blank");
    }

    @Test
    void handlesFeignExceptionAs502() {
        final Request request = Request.create(Request.HttpMethod.GET, "/x", Map.of(), null,
                StandardCharsets.UTF_8, new RequestTemplate());
        final FeignException ex = new FeignException.ServiceUnavailable("downstream unavailable", request, null,
                null);

        final ProblemDetail result = advice.handleFeignException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(result.getTitle()).isEqualTo("External Service Error");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/external-service-error");
    }

    @Test
    void handlesAccessDeniedExceptionAs403() {
        final AccessDeniedException ex = new AccessDeniedException("denied");

        final ProblemDetail result = advice.handleAccessDeniedException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.getDetail()).isEqualTo("ACCESS_DENIED");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/access-denied");
    }

    @Test
    void handlesGenericExceptionAs500() {
        final ProblemDetail result = advice.handleGenericException(new IllegalArgumentException("boom"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getDetail()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/internal-server-error");
    }

    private static final class TestAdvice extends AbstractProblemDetailAdvice {
    }

    /**
     * Minimal {@link MethodParameter} stand-in: {@link MethodArgumentNotValidException}
     * requires a real one, but nothing under test here reads anything from it
     * beyond construction.
     */
    private static final class MethodParameterStub extends MethodParameter {
        MethodParameterStub() throws NoSuchMethodException {
            super(ProblemDetailAdviceTest.class.getDeclaredMethod(
                    "handlesValidationExceptionAs400WithFieldMessages"), -1);
        }
    }
}
