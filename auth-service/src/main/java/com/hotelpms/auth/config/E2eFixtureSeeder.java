package com.hotelpms.auth.config;

import com.hotelpms.auth.domain.Role;
import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.repository.UserAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds a second-tenant ADMIN fixture used only by the local live E2E suite
 * (frontend/e2e-live/idor-cross-tenant-live.spec.ts), which needs a real,
 * independent identity in a DIFFERENT hotel to exercise cross-tenant
 * IDOR/RBAC checks against the real backend.
 *
 * <p><b>Opt-in, off by default everywhere.</b> This used to be a permanent
 * Flyway migration ({@code V7__seed_second_hotel_admin_for_e2e_tests.sql}),
 * which meant a fixture ADMIN account with a known password
 * ({@code password}, {@code must_change_password=false}) was created on
 * <em>every</em> installation, including a real hotel's — the migration
 * ran unconditionally, regardless of profile. {@code V8} removes that row
 * from any volume that already applied it; this runner replaces it with an
 * explicit, environment-gated seed that only ever runs when
 * {@code AUTH_SEED_E2E_FIXTURES=true} is set — never the default, never set
 * in {@code docker-compose.yml}/{@code docker-compose.prod.yml}, only by a
 * developer who deliberately wants to run {@code frontend/e2e-live} against
 * their local stack.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "auth", name = "seed-e2e-fixtures", havingValue = "true")
public class E2eFixtureSeeder implements ApplicationRunner {

    private static final String FIXTURE_USERNAME = "e2e-live-other-hotel-admin";
    private static final String FIXTURE_EMAIL = "e2e-live-other-hotel-admin@hotel-pms.local";
    private static final String FIXTURE_PASSWORD = "password";
    private static final UUID FIXTURE_HOTEL_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs the seeder.
     *
     * @param userRepository  repository used to check for and persist the fixture account
     * @param passwordEncoder encoder used to hash the fixture's known password
     */
    public E2eFixtureSeeder(final UserAccountRepository userRepository, final PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        log.warn("[AUTH] E2E_FIXTURE_SEED_ENABLED — auth.seed-e2e-fixtures=true. This creates an "
                + "ADMIN account with a publicly known password ({}). Never enable this on an "
                + "installation with real hotel data.", FIXTURE_USERNAME);

        if (userRepository.existsByUsername(FIXTURE_USERNAME)) {
            log.info("[AUTH] E2E_FIXTURE_SEED_SKIPPED | username={} | reason=ALREADY_EXISTS", FIXTURE_USERNAME);
            return;
        }

        final UserAccount fixture = UserAccount.builder()
                .username(FIXTURE_USERNAME)
                .passwordHash(passwordEncoder.encode(FIXTURE_PASSWORD))
                .email(FIXTURE_EMAIL)
                .role(Role.ADMIN)
                .hotelId(FIXTURE_HOTEL_ID)
                .active(true)
                .mustChangePassword(false)
                .build();
        userRepository.save(fixture);
        log.info("[AUTH] E2E_FIXTURE_SEED_CREATED | username={} | hotelId={}", FIXTURE_USERNAME, FIXTURE_HOTEL_ID);
    }
}
