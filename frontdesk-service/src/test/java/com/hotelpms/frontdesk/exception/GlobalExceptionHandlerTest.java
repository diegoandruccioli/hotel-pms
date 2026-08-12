package com.hotelpms.frontdesk.exception;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Targeted tests for {@link GlobalExceptionHandler#handleDataIntegrityViolationException}:
 * a NOT NULL constraint violation (SQLState 23502) must not be reported as
 * 409 "RESOURCE_ALREADY_EXISTS" — nothing already exists, some layer
 * upstream (frontend validation, typically) failed to require a field the
 * database does. Found via frontend/e2e-live/walk-in-live.spec.ts against
 * the real backend: a walk-in with no guest date of birth used to 409 with
 * a misleading message instead of a clear 400.
 */
class GlobalExceptionHandlerTest {

    private static final String INSERT_FAILED = "insert failed";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn400WhenCauseIsNotNullConstraintViolation() {
        final SQLException notNullViolation = new SQLException("null value in column \"date_of_birth\"", "23502");
        final DataIntegrityViolationException ex =
                new DataIntegrityViolationException(INSERT_FAILED, notNullViolation);

        final ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getDetail()).isEqualTo("REQUIRED_FIELD_MISSING");
    }

    @Test
    void shouldReturn409ForOtherIntegrityViolationsEgUniqueConstraint() {
        final SQLException uniqueViolation = new SQLException("duplicate key value", "23505");
        final DataIntegrityViolationException ex =
                new DataIntegrityViolationException(INSERT_FAILED, uniqueViolation);

        final ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getDetail()).isEqualTo("RESOURCE_ALREADY_EXISTS");
    }

    @Test
    void shouldReturn409WhenCauseIsNotASqlException() {
        final DataIntegrityViolationException ex =
                new DataIntegrityViolationException(INSERT_FAILED, new IllegalStateException("wrapped"));

        final ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getDetail()).isEqualTo("RESOURCE_ALREADY_EXISTS");
    }

    /**
     * frontdesk-service overrides the inherited generic catch-all to attach a
     * correlatable {@code traceId} — this must survive the migration to
     * {@code extends AbstractProblemDetailAdvice}, since no other service has
     * this behavior and it cannot live in the shared base.
     */
    @Test
    void handleGenericExceptionShouldStillAttachATraceId() {
        final ProblemDetail problemDetail = handler.handleGenericException(new RuntimeException("boom"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getDetail()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(problemDetail.getProperties()).containsKey("traceId");
        assertThat(problemDetail.getProperties().get("traceId")).asString().isNotBlank();
    }

    /**
     * {@code FeignException} used to be swallowed by this class's own
     * catch-all (500) before the migration to the shared base — now handled
     * by the inherited {@code AbstractProblemDetailAdvice#handleFeignException}.
     */
    @Test
    void handlesFeignExceptionAs502ViaInheritedHandler() {
        final Request request = Request.create(Request.HttpMethod.GET, "/x", Map.of(), null,
                StandardCharsets.UTF_8, new RequestTemplate());
        final FeignException ex = new FeignException.ServiceUnavailable("downstream unavailable", request, null,
                null);

        final ProblemDetail problemDetail = handler.handleFeignException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(Objects.requireNonNull(problemDetail.getType()).toString())
                .isEqualTo("https://hotel-pms.com/errors/external-service-error");
    }
}
