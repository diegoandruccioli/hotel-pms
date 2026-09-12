package com.hotelpms.frontdesk.stays.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts {@link com.hotelpms.frontdesk.stays.domain.StayGuest#getDocumentNumber()}
 * before it touches the database (E23, GDPR Art. 32).
 *
 * <p>Uses {@link Encryptors#delux}, Spring Security Crypto's AES-256-GCM
 * authenticated text encryptor — same choice, and same package, as {@link
 * AlloggiatiCredentialEncryptor}, but with its own dedicated key/salt
 * (different data, different service secret; never shared with the
 * Alloggiati Web credentials). GCM uses a random IV per call, so encrypting
 * the same plaintext twice never produces the same ciphertext.
 */
@Component
public class StayGuestDocumentEncryptor {

    private final TextEncryptor encryptor;

    /**
     * @param encryptionKey  the master password ({@code frontdesk.documents.encryption-key})
     * @param encryptionSalt a hex-encoded salt ({@code frontdesk.documents.encryption-salt})
     */
    public StayGuestDocumentEncryptor(
            @Value("${frontdesk.documents.encryption-key}") final String encryptionKey,
            @Value("${frontdesk.documents.encryption-salt}") final String encryptionSalt) {
        this.encryptor = Encryptors.delux(encryptionKey, encryptionSalt);
    }

    /**
     * Encrypts a plaintext document number.
     *
     * @param plaintext the raw value, may be {@code null} or blank
     * @return the ciphertext, or {@code null} if the input was {@code null}/blank
     */
    public String encrypt(final String plaintext) {
        return plaintext == null || plaintext.isBlank() ? null : encryptor.encrypt(plaintext);
    }

    /**
     * Decrypts a previously encrypted document number.
     *
     * @param ciphertext the encrypted value, may be {@code null} or blank
     * @return the plaintext, or {@code null} if the input was {@code null}/blank
     */
    public String decrypt(final String ciphertext) {
        return ciphertext == null || ciphertext.isBlank() ? null : encryptor.decrypt(ciphertext);
    }
}
