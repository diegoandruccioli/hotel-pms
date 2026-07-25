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
}
