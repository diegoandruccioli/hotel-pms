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

    @Mock
    private DocumentNumberEncryptor encryptor;

    @Test
    void convertToDatabaseColumnDelegatesToEncryptor() {
        when(encryptor.encrypt("AB123")).thenReturn("cipher");
        final DocumentNumberConverter converter = new DocumentNumberConverter(encryptor);

        assertEquals("cipher", converter.convertToDatabaseColumn("AB123"));
        verify(encryptor).encrypt("AB123");
    }

    @Test
    void convertToEntityAttributeDelegatesToEncryptor() {
        when(encryptor.decrypt("cipher")).thenReturn("AB123");
        final DocumentNumberConverter converter = new DocumentNumberConverter(encryptor);

        assertEquals("AB123", converter.convertToEntityAttribute("cipher"));
        verify(encryptor).decrypt("cipher");
    }
}
