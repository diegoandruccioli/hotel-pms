package com.hotelpms.frontdesk.stays.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StayGuestDocumentNumberConverter} — verifies it
 * delegates to {@link StayGuestDocumentEncryptor} rather than exercising
 * real crypto (covered by {@link StayGuestDocumentEncryptorTest}).
 */
@ExtendWith(MockitoExtension.class)
class StayGuestDocumentNumberConverterTest {

    @Mock
    private StayGuestDocumentEncryptor encryptor;

    @Test
    void convertToDatabaseColumnDelegatesToEncryptor() {
        when(encryptor.encrypt("AB123")).thenReturn("cipher");
        final StayGuestDocumentNumberConverter converter = new StayGuestDocumentNumberConverter(encryptor);

        assertEquals("cipher", converter.convertToDatabaseColumn("AB123"));
        verify(encryptor).encrypt("AB123");
    }

    @Test
    void convertToEntityAttributeDelegatesToEncryptor() {
        when(encryptor.decrypt("cipher")).thenReturn("AB123");
        final StayGuestDocumentNumberConverter converter = new StayGuestDocumentNumberConverter(encryptor);

        assertEquals("AB123", converter.convertToEntityAttribute("cipher"));
        verify(encryptor).decrypt("cipher");
    }
}
