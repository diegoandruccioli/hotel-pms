package com.hotelpms.guest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates the {@code guest.documents.encryption-key/-salt} pair at application
 * startup ({@link com.hotelpms.guest.security.DocumentNumberEncryptor}, GAP-28/E23).
 *
 * <p>{@code docker-compose.yml} falls back to a known-public placeholder pair
 * ({@code ci_placeholder_encryption_key} / {@code
 * deadbeefdeadbeefdeadbeefdeadbeef}) when the real env vars are not set — an
 * installation that forgets to configure them silently encrypts real guest
 * identity-document numbers with a key committed in this repository. Unlike
 * {@code internal.hmac.secret} in api-gateway (which already has this guard),
 * this pair had none.
 *
 * <p>If the pair is a known placeholder, blank, or too short, a blocking
 * {@link IllegalStateException} is thrown once the active Spring profile
 * indicates a real deployment (docker-compose sets {@code
 * SPRING_PROFILES_ACTIVE=guest-service}). The check degrades to a WARN when no
 * profile is active or it contains {@code dev}/{@code test} — every local
 * Gradle test run and ad-hoc {@code java -jar} without Docker — so those keep
 * working without a configured secret.
 */
@Slf4j
@Component
public class EncryptionKeyStartupCheck {

    private static final int MIN_KEY_LENGTH = 16;

    private static final Set<String> KNOWN_INSECURE_KEYS = Set.of(
            "ci_placeholder_encryption_key",
            ""
    );

    private static final Set<String> KNOWN_INSECURE_SALTS = Set.of(
            "deadbeefdeadbeefdeadbeefdeadbeef",
            ""
    );

    private final String encryptionKey;
    private final String encryptionSalt;
    private final String activeProfile;

    /**
     * Constructs the check component.
     *
     * @param encryptionKey  encryption key for {@code IdentityDocument.documentNumber}
     * @param encryptionSalt encryption salt for {@code IdentityDocument.documentNumber}
     * @param activeProfile  the active Spring profiles (may be empty)
     */
    public EncryptionKeyStartupCheck(
            @Value("${guest.documents.encryption-key:}") final String encryptionKey,
            @Value("${guest.documents.encryption-salt:}") final String encryptionSalt,
            @Value("${spring.profiles.active:}") final String activeProfile) {
        this.encryptionKey = encryptionKey;
        this.encryptionSalt = encryptionSalt;
        this.activeProfile = activeProfile;
    }

    /**
     * Runs the encryption key/salt validation after the application context is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateEncryptionKey() {
        final boolean insecure = KNOWN_INSECURE_KEYS.contains(encryptionKey.trim())
                || KNOWN_INSECURE_SALTS.contains(encryptionSalt.trim())
                || encryptionKey.length() < MIN_KEY_LENGTH;

        if (!insecure) {
            log.info("[GUEST] ENCRYPTION_KEY_OK");
            return;
        }

        final boolean isDevOrTest = activeProfile.isBlank()
                || activeProfile.contains("dev") || activeProfile.contains("test");
        if (isDevOrTest) {
            log.warn("[GUEST] ENCRYPTION_KEY_INSECURE — acceptable in dev/test but MUST be changed "
                    + "before a real hotel's data touches this installation");
        } else {
            log.error("[GUEST] ENCRYPTION_KEY_INSECURE — refusing to start with a placeholder, "
                    + "blank, or short guest.documents.encryption-key/-salt. Set real values (see "
                    + ".env.example) before starting on an installation with real guest data.");
            throw new IllegalStateException(
                    "guest.documents.encryption-key/-salt is insecure. "
                            + "Set strong values before starting in production.");
        }
    }
}
