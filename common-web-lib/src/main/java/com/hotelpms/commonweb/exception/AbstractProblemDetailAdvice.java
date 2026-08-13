package com.hotelpms.commonweb.exception;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base RFC 7807 Problem Details exception handling shared by every internal
 * service: malformed request bodies, bean-validation failures, downstream
 * Feign failures, {@code @PreAuthorize} denials, and the generic 500
 * catch-all.
 *
 * <p>NOT annotated {@code @RestControllerAdvice} itself — only concrete
 * per-service subclasses are, so Spring registers exactly one advice bean per
 * service. Spring MVC picks up {@code @ExceptionHandler} methods declared on
 * superclasses of the registered advice bean, so subclassing is sufficient;
 * no explicit re-declaration is needed.
 *
 * <p>Exception types that differ per service (each service's own
 * {@code NotFoundException}, domain-specific {@code ExternalServiceException},
 * etc.) are NOT handled here — those stay in each service's own subclass,
 * since the types themselves live in separate per-service packages.
 */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class AbstractProblemDetailAdvice {

    /**
     * Property key under which every {@link ProblemDetail} response here
     * carries its generation timestamp.
     */
    protected static final String TIMESTAMP_FIELD = "timestamp";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProblemDetailAdvice.class);
    private static final String ERRORS_BASE_URI = "https://hotel-pms.com/errors/";
    private static final String BAD_REQUEST_TITLE = "Request Validation Error";
    private static final String BAD_REQUEST_SLUG = "bad-request";

    /**
     * Builds the {@code type} URI for a given error slug, rooted at the
     * shared {@code https://hotel-pms.com/errors/} namespace used by every
     * internal service's Problem Details responses.
     *
     * @param slug the error-specific path segment, e.g. {@code "not-found"}
     * @return the absolute {@code type} URI
     */
    protected static URI errorType(final String slug) {
        return Objects.requireNonNull(URI.create(ERRORS_BASE_URI + slug));
    }

    /**
     * Handles malformed request bodies (invalid JSON, unparseable enum values, etc.).
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(final HttpMessageNotReadableException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "INVALID_JSON_PAYLOAD");
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(errorType(BAD_REQUEST_SLUG));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles MethodArgumentNotValidException for DTO validation.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status and field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(final MethodArgumentNotValidException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(errorType(BAD_REQUEST_SLUG));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());

        final List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map((@NonNull FieldError fe) -> fe.getDefaultMessage())
                .collect(Collectors.toList());
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * Handles a missing required {@code @RequestParam} (e.g. a mandatory query
     * parameter such as {@code startDate}/{@code endDate} omitted from the
     * request). Must be explicit so the generic 500 catch-all below does not
     * swallow it — this is always a client input error, never a server fault,
     * regardless of which endpoint or service raises it.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletRequestParameterException(
            final MissingServletRequestParameterException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "MISSING_REQUIRED_PARAMETER: " + ex.getParameterName());
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(errorType(BAD_REQUEST_SLUG));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles FeignException — a call to a downstream internal service failed
     * or returned an unexpected result.
     *
     * @param ex the exception
     * @return ProblemDetail with 502 status
     */
    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleFeignException(final FeignException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problemDetail.setTitle("External Service Error");
        problemDetail.setType(errorType("external-service-error"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles AccessDeniedException thrown by {@code @PreAuthorize} when the
     * caller's role is not in the allowed set. Must be explicit so the catch-all
     * handler below does not swallow it and return 500 instead of 403.
     *
     * @param ex the exception
     * @return ProblemDetail with 403 status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(final AccessDeniedException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(errorType("access-denied"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles a request path that matches no controller mapping and no static
     * resource. Must be explicit so the generic 500 catch-all below does not
     * swallow it and return 500 instead of 404 — same rationale as
     * {@link #handleAccessDeniedException(AccessDeniedException)}.
     *
     * @param ex the exception
     * @return ProblemDetail with 404 status
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(final NoResourceFoundException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "NOT_FOUND");
        problemDetail.setTitle("Not Found");
        problemDetail.setType(errorType("not-found"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles a path variable or request parameter that could not be
     * converted to its target type (e.g. a malformed UUID). Must be explicit
     * so the generic 500 catch-all below does not swallow it — this is
     * always a client input error, never a server fault.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatchException(final MethodArgumentTypeMismatchException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_TYPE: " + ex.getName());
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(errorType(BAD_REQUEST_SLUG));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles a {@code Pageable}/{@code Sort} request parameter (e.g.
     * {@code ?sort=noSuchField}) referencing a property that does not exist on the
     * target entity. Spring Data validates the sort path against the JPA metamodel
     * before any SQL is generated — this is a client input error, never a server
     * fault or an injection vector — but without an explicit handler it fell
     * through the generic 500 catch-all below.
     *
     * @param ex the exception
     * @return ProblemDetail with 400 status
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handlePropertyReferenceException(final PropertyReferenceException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "INVALID_SORT_PROPERTY: " + ex.getPropertyName());
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(errorType(BAD_REQUEST_SLUG));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles a request made with an HTTP method the matched endpoint does
     * not support. Must be explicit so the generic 500 catch-all below does
     * not swallow it.
     *
     * @param ex the exception
     * @return ProblemDetail with 405 status
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleHttpRequestMethodNotSupportedException(final HttpRequestMethodNotSupportedException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED");
        problemDetail.setTitle("Method Not Allowed");
        problemDetail.setType(errorType("method-not-allowed"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles a request whose {@code Content-Type} is not supported by the
     * matched endpoint. Must be explicit so the generic 500 catch-all below
     * does not swallow it.
     *
     * @param ex the exception
     * @return ProblemDetail with 415 status
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleHttpMediaTypeNotSupportedException(final HttpMediaTypeNotSupportedException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE");
        problemDetail.setTitle("Unsupported Media Type");
        problemDetail.setType(errorType("unsupported-media-type"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }

    /**
     * Handles generic exceptions.
     *
     * @param ex the exception
     * @return ProblemDetail with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(final Exception ex) {
        LOG.error("Unhandled exception: {}", ex.getMessage(), ex);
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(errorType("internal-server-error"));
        problemDetail.setProperty(TIMESTAMP_FIELD, Instant.now());
        return problemDetail;
    }
}
