package com.hotelpms.fb.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Finding #17 (security-report.md, LOW): {@code ExternalServiceException}'s
 * message used to be exposed verbatim in the 502 body, typically including
 * the internal Docker service URL the failed call targeted.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ExternalServiceException detail is generic, never the raw downstream message")
    void externalServiceExceptionHasGenericDetailNotRawMessage() {
        final ExternalServiceException ex =
                new ExternalServiceException("call to http://guest-service:8083/api/v1/guests/123 failed");

        final ProblemDetail problemDetail = handler.handleExternalServiceException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY.value(), problemDetail.getStatus());
        assertEquals("EXTERNAL_SERVICE_ERROR", problemDetail.getDetail());
    }
}
