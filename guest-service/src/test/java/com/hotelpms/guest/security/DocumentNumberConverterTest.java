package com.hotelpms.guest.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentNumberConverter} — verifies it delegates to
 * {@link DocumentNumberEncryptor} rather than exercising real crypto (covered
 * by {@link DocumentNumberEncryptorTest}).
 */
@ExtendWith(MockitoExtension.class)
class DocumentNumberConverterTest {

    private static final String PLAINTEXT = "AB123";
    private static final String CIPHERTEXT = "cipher";

    @Mock
    private DocumentNumberEncryptor encryptor;

    @Test
    void convertToDatabaseColumnDelegatesToEncryptor() {
        when(encryptor.encrypt(PLAINTEXT)).thenReturn(CIPHERTEXT);
        final DocumentNumberConverter converter = new DocumentNumberConverter(encryptor);

        assertEquals(CIPHERTEXT, converter.convertToDatabaseColumn(PLAINTEXT));
        verify(encryptor).encrypt(PLAINTEXT);
    }

    @Test
    void convertToEntityAttributeDelegatesToEncryptor() {
        when(encryptor.decrypt(CIPHERTEXT)).thenReturn(PLAINTEXT);
        final DocumentNumberConverter converter = new DocumentNumberConverter(encryptor);

        assertEquals(PLAINTEXT, converter.convertToEntityAttribute(CIPHERTEXT));
        verify(encryptor).decrypt(CIPHERTEXT);
    }
}
