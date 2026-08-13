package com.hotelpms.commonweb.exception;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Unit tests for {@link AbstractProblemDetailAdvice} via a trivial concrete
 * subclass, mirroring how billing-service/fb-service/guest-service's own
 * {@code GlobalExceptionHandler} extends it in production.
 */
class ProblemDetailAdviceTest {

    private static final String BAD_REQUEST_TYPE = "https://hotel-pms.com/errors/bad-request";
    private static final String REQUEST_VALIDATION_ERROR_TITLE = "Request Validation Error";
    private static final String TIMESTAMP_KEY = "timestamp";

    private final TestAdvice advice = new TestAdvice();

    @Test
    @SuppressWarnings("deprecation")
    void handlesHttpMessageNotReadableExceptionAs400() {
        final HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json");

        final ProblemDetail result = advice.handleHttpMessageNotReadableException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo(REQUEST_VALIDATION_ERROR_TITLE);
        assertThat(result.getType().toString()).isEqualTo(BAD_REQUEST_TYPE);
        assertThat(result.getDetail()).isEqualTo("INVALID_JSON_PAYLOAD");
        assertThat(result.getProperties()).containsKey(TIMESTAMP_KEY);
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
        assertThat(result.getType().toString()).isEqualTo(BAD_REQUEST_TYPE);
        final Object errorsProperty = Objects.requireNonNull(result.getProperties()).get("errors");
        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) Objects.requireNonNull(errorsProperty);
        assertThat(errors).containsExactly("must not be blank");
    }

    @Test
    void handlesMissingServletRequestParameterExceptionAs400() {
        final MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("startDate", "LocalDate");

        final ProblemDetail result = advice.handleMissingServletRequestParameterException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo(REQUEST_VALIDATION_ERROR_TITLE);
        assertThat(result.getType().toString()).isEqualTo(BAD_REQUEST_TYPE);
        assertThat(result.getDetail()).isEqualTo("MISSING_REQUIRED_PARAMETER: startDate");
        assertThat(result.getProperties()).containsKey(TIMESTAMP_KEY);
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

    @Test
    void handlesNoResourceFoundExceptionAs404() {
        final NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "actuator/health");

        final ProblemDetail result = advice.handleNoResourceFoundException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getDetail()).isEqualTo("NOT_FOUND");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/not-found");
        assertThat(result.getProperties()).containsKey(TIMESTAMP_KEY);
    }

    @Test
    void handlesMethodArgumentTypeMismatchExceptionAs400() throws NoSuchMethodException {
        final MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-valid-uuid", java.util.UUID.class, "id", new MethodParameterStub(), null);

        final ProblemDetail result = advice.handleMethodArgumentTypeMismatchException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo(REQUEST_VALIDATION_ERROR_TITLE);
        assertThat(result.getType().toString()).isEqualTo(BAD_REQUEST_TYPE);
        assertThat(result.getDetail()).isEqualTo("INVALID_PARAMETER_TYPE: id");
    }

    @Test
    void handlesHttpRequestMethodNotSupportedExceptionAs405() {
        final HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        final ProblemDetail result = advice.handleHttpRequestMethodNotSupportedException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(result.getDetail()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/method-not-allowed");
    }

    @Test
    void handlesHttpMediaTypeNotSupportedExceptionAs415() {
        final HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));

        final ProblemDetail result = advice.handleHttpMediaTypeNotSupportedException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(result.getDetail()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(result.getType().toString()).isEqualTo("https://hotel-pms.com/errors/unsupported-media-type");
    }

    /**
     * {@code ?sort=doesNotExist} on a paginated endpoint (e.g. InvoiceController,
     * RestaurantOrderController) used to fall through to the generic 500 catch-all
     * before this handler existed. Uses a real {@link PropertyReferenceException},
     * obtained the same way Spring Data itself produces one — resolving a
     * {@link PropertyPath} against a type that has no such property — rather than
     * hand-constructing it, since the exact constructor is an internal API detail.
     */
    @Test
    void handlesPropertyReferenceExceptionAs400() {
        final PropertyReferenceException ex = catchThrowableOfType(
                () -> PropertyPath.from("doesNotExist", TestAdvice.class),
                PropertyReferenceException.class);

        final ProblemDetail result = advice.handlePropertyReferenceException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo(REQUEST_VALIDATION_ERROR_TITLE);
        assertThat(result.getType().toString()).isEqualTo(BAD_REQUEST_TYPE);
        assertThat(result.getDetail()).isEqualTo("INVALID_SORT_PROPERTY: doesNotExist");
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
