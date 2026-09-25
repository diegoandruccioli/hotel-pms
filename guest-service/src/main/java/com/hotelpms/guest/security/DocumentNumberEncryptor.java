package com.hotelpms.guest.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts {@link com.hotelpms.guest.model.IdentityDocument#getDocumentNumber()}
 * before it touches the database (E23, GDPR Art. 32).
 *
 * <p>Uses {@link Encryptors#delux}, Spring Security Crypto's AES-256-GCM
 * authenticated text encryptor (ADR-002: a maintained library, not
 * hand-rolled cipher code) — same choice already made for the Alloggiati Web
 * credentials in frontdesk-service's {@code AlloggiatiCredentialEncryptor}.
 * GCM uses a random IV per call, so encrypting the same plaintext twice never
 * produces the same ciphertext.
 */
@Component
public class DocumentNumberEncryptor {

    private final TextEncryptor encryptor;

    /**
     * @param encryptionKey  the master password ({@code guest.documents.encryption-key})
     * @param encryptionSalt a hex-encoded salt ({@code guest.documents.encryption-salt})
     */
    public DocumentNumberEncryptor(
            @Value("${guest.documents.encryption-key}") final String encryptionKey,
            @Value("${guest.documents.encryption-salt}") final String encryptionSalt) {
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
