package com.hotelpms.frontdesk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates the PII/credential encryption keys and salts at application startup.
 *
 * <p>{@code frontdesk.documents.encryption-key/-salt} (StayGuest document numbers,
 * GAP-28/E23) and {@code alloggiati.credentials.encryption-key/-salt} (per-hotel
 * Alloggiati Web password/WsKey, P8) both fall back in {@code docker-compose.yml}
 * to the same literal placeholder pair ({@code ci_placeholder_encryption_key} /
 * {@code deadbeefdeadbeefdeadbeefdeadbeef}) when the real env vars are not set —
 * meaning an installation that forgets to configure them silently encrypts real
 * guest PII with a key that is public, committed in this repository's own
 * {@code docker-compose.yml}. Unlike {@code internal.hmac.secret} in api-gateway
 * (which already has this guard), these two pairs had none.
 *
 * <p>If either pair is a known placeholder, blank, or too short, a blocking
 * {@link IllegalStateException} is thrown once the active Spring profile
 * indicates a real deployment (docker-compose sets {@code
 * SPRING_PROFILES_ACTIVE=frontdesk-service}). The check degrades to a WARN when
 * no profile is active or it contains {@code dev}/{@code test} — every local
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

    private final String frontdeskDocumentsKey;
    private final String frontdeskDocumentsSalt;
    private final String alloggiatiCredentialsKey;
    private final String alloggiatiCredentialsSalt;
    private final String activeProfile;

    /**
     * Constructs the check component.
     *
     * @param frontdeskDocumentsKey     encryption key for {@code StayGuest.documentNumber}
     * @param frontdeskDocumentsSalt    encryption salt for {@code StayGuest.documentNumber}
     * @param alloggiatiCredentialsKey  encryption key for per-hotel Alloggiati credentials
     * @param alloggiatiCredentialsSalt encryption salt for per-hotel Alloggiati credentials
     * @param activeProfile             the active Spring profiles (may be empty)
     */
    public EncryptionKeyStartupCheck(
            @Value("${frontdesk.documents.encryption-key:}") final String frontdeskDocumentsKey,
            @Value("${frontdesk.documents.encryption-salt:}") final String frontdeskDocumentsSalt,
            @Value("${alloggiati.credentials.encryption-key:}") final String alloggiatiCredentialsKey,
            @Value("${alloggiati.credentials.encryption-salt:}") final String alloggiatiCredentialsSalt,
            @Value("${spring.profiles.active:}") final String activeProfile) {
        this.frontdeskDocumentsKey = frontdeskDocumentsKey;
        this.frontdeskDocumentsSalt = frontdeskDocumentsSalt;
        this.alloggiatiCredentialsKey = alloggiatiCredentialsKey;
        this.alloggiatiCredentialsSalt = alloggiatiCredentialsSalt;
        this.activeProfile = activeProfile;
    }

    /**
     * Runs the encryption key/salt validation after the application context is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateEncryptionKeys() {
        final boolean frontdeskInsecure = isInsecure(frontdeskDocumentsKey, frontdeskDocumentsSalt);
        final boolean alloggiatiInsecure = isInsecure(alloggiatiCredentialsKey, alloggiatiCredentialsSalt);

        if (!frontdeskInsecure && !alloggiatiInsecure) {
            log.info("[FRONTDESK] ENCRYPTION_KEYS_OK");
            return;
        }

        final List<String> insecureNames = new ArrayList<>();
        if (frontdeskInsecure) {
            insecureNames.add("frontdesk.documents.encryption-key/-salt");
        }
        if (alloggiatiInsecure) {
            insecureNames.add("alloggiati.credentials.encryption-key/-salt");
        }

        final boolean isDevOrTest = activeProfile.isBlank()
                || activeProfile.contains("dev") || activeProfile.contains("test");
        if (isDevOrTest) {
            log.warn("[FRONTDESK] ENCRYPTION_KEYS_INSECURE | pairs={} — acceptable in dev/test but "
                    + "MUST be changed before a real hotel's data touches this installation", insecureNames);
        } else {
            log.error("[FRONTDESK] ENCRYPTION_KEYS_INSECURE | pairs={} — refusing to start with a "
                    + "placeholder, blank, or short encryption key/salt. Set real values (see "
                    + ".env.example) before starting on an installation with real guest data.", insecureNames);
            throw new IllegalStateException(
                    "One or more PII encryption key/salt pairs are insecure: " + insecureNames
                            + ". Set strong values before starting in production.");
        }
    }

    private static boolean isInsecure(final String key, final String salt) {
        return KNOWN_INSECURE_KEYS.contains(key.trim())
                || KNOWN_INSECURE_SALTS.contains(salt.trim())
                || key.length() < MIN_KEY_LENGTH;
    }
}
