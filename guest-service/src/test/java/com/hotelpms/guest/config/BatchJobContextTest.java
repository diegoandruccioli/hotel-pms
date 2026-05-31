package com.hotelpms.guest.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BatchJobContextTest {

    @AfterEach
    void cleanup() {
        BatchJobContext.clear();
    }

    @Test
    void setAndGetReturnContextWithCorrectValues() {
        BatchJobContext.set("hotel-123");

        final BatchJobContext ctx = BatchJobContext.get();

        assertNotNull(ctx);
        assertEquals("gdpr-retention-job", ctx.getUser());
        assertEquals("ADMIN", ctx.getRole());
        assertEquals("hotel-123", ctx.getHotelId());
    }

    @Test
    void getReturnsNullWhenNotSet() {
        assertNull(BatchJobContext.get());
    }

    @Test
    void clearRemovesContext() {
        BatchJobContext.set("hotel-abc");
        BatchJobContext.clear();

        assertNull(BatchJobContext.get());
    }

    @Test
    void setOverwritesPreviousContext() {
        BatchJobContext.set("hotel-first");
        BatchJobContext.set("hotel-second");

        final BatchJobContext ctx = BatchJobContext.get();

        assertNotNull(ctx);
        assertEquals("hotel-second", ctx.getHotelId());
    }
}
