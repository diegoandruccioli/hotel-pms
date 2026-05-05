package com.hotelpms.gateway.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates the {@code internal.hmac.secret} at application startup.
 *
 * <p>If the secret is a known placeholder or is too short to be secure,
 * a blocking {@link IllegalStateException} is thrown in production mode.
 * When {@code spring.profiles.active=dev} or {@code test} the check
 * degrades to a WARN so local development still works without a configured secret.
 */
@Component
public class HmacSecretStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(HmacSecretStartupCheck.class);

    private static final int MIN_SECRET_LENGTH = 32;

    private static final Set<String> KNOWN_INSECURE_SECRETS = Set.of(
            "change-me-before-production",
            "ci_placeholder_hmac",
            "test-internal-hmac-secret-for-unit-tests",
            ""
    );

    private final String hmacSecret;
    private final String activeProfile;

    /**
     * Constructs the check component.
     *
     * @param hmacSecret    the shared HMAC secret from environment
     * @param activeProfile the active Spring profiles (may be empty)
     */
    public HmacSecretStartupCheck(
            @Value("${internal.hmac.secret}") final String hmacSecret,
            @Value("${spring.profiles.active:}") final String activeProfile) {
        this.hmacSecret = hmacSecret;
        this.activeProfile = activeProfile;
    }

    /**
     * Runs the HMAC secret validation after the application context is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateHmacSecret() {
        final boolean insecure = KNOWN_INSECURE_SECRETS.contains(hmacSecret.trim())
                || hmacSecret.length() < MIN_SECRET_LENGTH;

        if (!insecure) {
            log.info("[GATEWAY] HMAC_SECRET_OK | length={}", hmacSecret.length());
            return;
        }

        final boolean isDevOrTest = activeProfile.contains("dev") || activeProfile.contains("test");
        if (isDevOrTest) {
            log.warn("[GATEWAY] HMAC_SECRET_INSECURE — acceptable in dev/test but MUST be changed before production");
        } else {
            log.error("[GATEWAY] HMAC_SECRET_INSECURE — refusing to start with a placeholder or short secret. "
                    + "Set a secure INTERNAL_HMAC_SECRET (min {} chars) in your environment.", MIN_SECRET_LENGTH);
            throw new IllegalStateException(
                    "INTERNAL_HMAC_SECRET is insecure. Set a strong secret before starting in production.");
        }
    }
}
