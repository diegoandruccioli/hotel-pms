package com.hotelpms.notification.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailMaskerTest {

    private static final String SENTINEL = "***";

    @Test
    void shouldMaskStandardEmailAddress() {
        assertEquals("a***@example.com", EmailMasker.mask("alice@example.com"));
    }

    @Test
    void shouldKeepOnlyFirstCharBeforeAt() {
        assertEquals("j***@test.org", EmailMasker.mask("john@test.org"));
    }

    @Test
    void shouldReturnSentinelForNullInput() {
        assertEquals(SENTINEL, EmailMasker.mask(null));
    }

    @Test
    void shouldReturnSentinelForMissingAtSign() {
        assertEquals(SENTINEL, EmailMasker.mask("notanemail"));
    }

    @Test
    void shouldReturnSentinelForEmptyString() {
        assertEquals(SENTINEL, EmailMasker.mask(""));
    }

    @Test
    void shouldHandleSingleCharLocalPart() {
        assertEquals("a***@x.io", EmailMasker.mask("a@x.io"));
    }
}
