package com.hotelpms.billing.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1: {@link GlobalExceptionHandler#handleOptimisticLockingFailure} must map a
 * concurrent-modification conflict on {@code Invoice} to HTTP 409, not fall through
 * to the generic 500 handler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException maps to 409 Conflict, not 500")
    void optimisticLockingFailureMapsTo409() {
        final ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Invoice", "some-id");

        final ProblemDetail problemDetail = handler.handleOptimisticLockingFailure(ex);

        assertEquals(HttpStatus.CONFLICT.value(), problemDetail.getStatus());
        assertEquals("Conflict", problemDetail.getTitle());
    }

    @Test
    @DisplayName("ExternalServiceException detail is generic, never the raw downstream message (Finding #17)")
    void externalServiceExceptionHasGenericDetailNotRawMessage() {
        final ExternalServiceException ex =
                new ExternalServiceException("call to http://frontdesk-service:8081/api/v1/rooms/123 failed");

        final ProblemDetail problemDetail = handler.handleExternalServiceException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY.value(), problemDetail.getStatus());
        assertEquals("EXTERNAL_SERVICE_ERROR", problemDetail.getDetail());
    }
}
