package com.hotelpms.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the auth-service-specific handlers, plus a check that
 * {@code AccessDeniedException} (from {@code @PreAuthorize} denials on
 * {@code UserManagementController}) now resolves via the inherited {@code
 * AbstractProblemDetailAdvice} handler as 403 — before this class extended
 * the shared base, it fell through to the local catch-all and returned 500.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesAccountLockedExceptionAs429() {
        final ProblemDetail result = handler.handleAccountLockedException(
                new AccountLockedException("too many attempts"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(result.getTitle()).isEqualTo("Account Temporarily Locked");
    }

    @Test
    void handlesBadCredentialsExceptionAs401() {
        final ProblemDetail result = handler.handleBadCredentialsException(
                new BadCredentialsException("bad password"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.getTitle()).isEqualTo("Authentication Failed");
    }

    @Test
    void handlesDuplicateResourceExceptionAs409() {
        final ProblemDetail result = handler.handleDuplicateResourceException(
                new DuplicateResourceException("username taken"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getTitle()).isEqualTo("Resource Conflict");
    }

    @Test
    void handlesNotFoundExceptionAs404() {
        final ProblemDetail result = handler.handleNotFoundException(new NotFoundException("user not found"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getTitle()).isEqualTo("Not Found");
    }

    @Test
    void handlesIllegalStateExceptionAs409() {
        final ProblemDetail result = handler.handleIllegalStateException(
                new IllegalStateException("cannot self-deactivate"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getTitle()).isEqualTo("Conflict");
    }

    @Test
    void inheritsAccessDeniedExceptionAs403NotTheOldFallthroughTo500() {
        final ProblemDetail result = handler.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.getDetail()).isEqualTo("ACCESS_DENIED");
    }
}
