package com.hotelpms.guest.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link DocumentNumberEncryptor}.
 */
class DocumentNumberEncryptorTest {

    private static final String KEY = "test-encryption-key";
    private static final String SALT = "deadbeefdeadbeefdeadbeefdeadbeef";
    private static final String PLAINTEXT = "AB1234567";

    private DocumentNumberEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new DocumentNumberEncryptor(KEY, SALT);
    }

    @Test
    void encryptsAndDecryptsRoundTrip() {
        final String ciphertext = encryptor.encrypt(PLAINTEXT);

        assertNotEquals(PLAINTEXT, ciphertext);
        assertEquals(PLAINTEXT, encryptor.decrypt(ciphertext));
    }

    @Test
    void encryptingTheSamePlaintextTwiceProducesDifferentCiphertext() {
        // GCM uses a random IV per call — two encryptions of the same value
        // must never produce identical ciphertext.
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
