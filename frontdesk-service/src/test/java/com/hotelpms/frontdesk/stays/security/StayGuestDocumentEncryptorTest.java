package com.hotelpms.frontdesk.stays.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link StayGuestDocumentEncryptor}.
 */
class StayGuestDocumentEncryptorTest {

    private static final String KEY = "test-encryption-key";
    private static final String SALT = "deadbeefdeadbeefdeadbeefdeadbeef";
    private static final String PLAINTEXT = "AB1234567";

    private StayGuestDocumentEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new StayGuestDocumentEncryptor(KEY, SALT);
    }

    @Test
    void encryptsAndDecryptsRoundTrip() {
        final String ciphertext = encryptor.encrypt(PLAINTEXT);

        assertNotEquals(PLAINTEXT, ciphertext);
        assertEquals(PLAINTEXT, encryptor.decrypt(ciphertext));
    }

    @Test
    void encryptingTheSamePlaintextTwiceProducesDifferentCiphertext() {
        final String first = encryptor.encrypt(PLAINTEXT);
        final String second = encryptor.encrypt(PLAINTEXT);

        assertNotEquals(first, second);
        assertEquals(PLAINTEXT, encryptor.decrypt(first));
        assertEquals(PLAINTEXT, encryptor.decrypt(second));
    }

    @Test
    void encryptReturnsNullForNullInput() {
        assertNull(encryptor.encrypt(null));
    }

    @Test
    void encryptReturnsNullForBlankInput() {
        assertNull(encryptor.encrypt("   "));
    }

    @Test
    void decryptReturnsNullForNullInput() {
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void decryptReturnsNullForBlankInput() {
        assertNull(encryptor.decrypt(""));
    }
}
