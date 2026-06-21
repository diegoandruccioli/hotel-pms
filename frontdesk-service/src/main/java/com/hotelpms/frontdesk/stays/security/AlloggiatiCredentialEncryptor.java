package com.hotelpms.frontdesk.stays.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts per-hotel Alloggiati Web credentials (password, WsKey)
 * before they touch the database.
 *
 * <p>Uses {@link Encryptors#delux}, Spring Security Crypto's AES-256-GCM
 * authenticated text encryptor (ADR-002: a maintained library, not hand-rolled
 * cipher code). GCM uses a random IV per call, so encrypting the same
 * plaintext twice never produces the same ciphertext.
 */
@Component
public class AlloggiatiCredentialEncryptor {

    private final TextEncryptor encryptor;

    /**
     * @param encryptionKey  the master password ({@code alloggiati.credentials.encryption-key})
     * @param encryptionSalt a hex-encoded salt ({@code alloggiati.credentials.encryption-salt})
     */
    public AlloggiatiCredentialEncryptor(
            @Value("${alloggiati.credentials.encryption-key}") final String encryptionKey,
            @Value("${alloggiati.credentials.encryption-salt}") final String encryptionSalt) {
        this.encryptor = Encryptors.delux(encryptionKey, encryptionSalt);
    }

    /**
     * Encrypts a plaintext credential value.
     *
     * @param plaintext the raw value, may be {@code null} or blank
     * @return the ciphertext, or {@code null} if the input was {@code null}/blank
     */
    public String encrypt(final String plaintext) {
        return plaintext == null || plaintext.isBlank() ? null : encryptor.encrypt(plaintext);
    }

    /**
     * Decrypts a previously encrypted credential value.
     *
     * @param ciphertext the encrypted value, may be {@code null} or blank
     * @return the plaintext, or {@code null} if the input was {@code null}/blank
     */
    public String decrypt(final String ciphertext) {
        return ciphertext == null || ciphertext.isBlank() ? null : encryptor.decrypt(ciphertext);
    }
}
