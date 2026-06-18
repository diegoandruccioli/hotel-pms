package com.hotelpms.frontdesk.stays.controller;

import com.hotelpms.frontdesk.security.NonceStore;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiRowDto;
import com.hotelpms.frontdesk.security.SecurityConfig;
import com.hotelpms.frontdesk.stays.service.AlloggiatiReportService;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.StayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-level security tests for StayController endpoints protected by
 * {@code @PreAuthorize("hasAnyRole('ADMIN','OWNER')")}.
 *
 * <p>Default Spring Security auto-configurations are excluded so that only
 * {@link SecurityConfig} (our custom configuration) processes requests.
 * {@link com.hotelpms.frontdesk.security.InternalAuthFilter} validates HMAC headers
 * and {@link org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity}
 * enforces {@code @PreAuthorize} via AOP.
 *
 * <p>{@link NonceStore} is mocked (T-GW-08 anti-replay) rather than backed by a real
 * Redis connection — this is a slice test with no Redis in the context, and the
 * filter's HMAC/timestamp logic is what's under test here, not the nonce store itself
 * (covered separately by {@code InternalAuthFilterTest} in fb-service).
 *
 * <p>The HMAC secret matches {@code src/test/resources/application.yml}.
 */
@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@WebMvcTest(
        controllers = StayController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
class StayControllerSecurityTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TEST_SECRET =
            "test-hmac-secret-minimum-32-characters-for-unit-tests";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private static final String PATH_SUBMIT = "/api/v1/stays/reports/alloggiati/submit";
    private static final String PATH_JSON = "/api/v1/stays/reports/alloggiati/json";
    private static final String PARAM_DATE = "date";
    private static final String TEST_DATE = "2026-05-17";

    private static final String HDR_USER = "X-Auth-User";
    private static final String HDR_ROLE = "X-Auth-Role";
    private static final String HDR_HOTEL = "X-Auth-Hotel";
    private static final String HDR_SIG = "X-Internal-Signature";
    private static final String HDR_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HDR_NONCE = "X-Auth-Nonce";

    private static final String USER_RECEPT = "recept";
    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    private static final String USER_ADMIN = "admin";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String USER_OWNER = "owner";
    private static final String ROLE_OWNER = "OWNER";
    private static final String STATO_CODE = "Z000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private StayService stayService;

    @MockitoBean
    private AlloggiatiReportService alloggiatiReportService;

    @MockitoBean
    private AlloggiatiWebSenderService alloggiatiWebSenderService;

    @MockitoBean
    private NonceStore nonceStore;

    @BeforeEach
    void stubNonceStoreAsAlwaysFresh() {
        // Every request in this class carries a brand-new nonce (see withAuthHeaders);
        // replay detection itself is covered by InternalAuthFilterTest, not here.
        when(nonceStore.claim(anyString(), anyLong())).thenReturn(true);
    }

    // ──────────────────────────────── submit Alloggiati ────────────────────

    @Test
    void submitAlloggiatiReportReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(post(PATH_SUBMIT).param(PARAM_DATE, TEST_DATE),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitAlloggiatiReportReturns200ForAdmin() throws Exception {
        doNothing().when(alloggiatiWebSenderService).submitReport(any());

        mockMvc.perform(withAuthHeaders(post(PATH_SUBMIT).param(PARAM_DATE, TEST_DATE),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────── JSON export ──────────────────────────

    @Test
    void downloadAlloggiatiJsonReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(get(PATH_JSON).param(PARAM_DATE, TEST_DATE),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadAlloggiatiJsonReturns200ForOwner() throws Exception {
        when(alloggiatiReportService.generateJsonReport(any())).thenReturn(List.of(
                new AlloggiatiRowDto("16", "17/05/2026", 1, "Rossi", "Mario",
                        "1", "01/01/1980", "", "", STATO_CODE, STATO_CODE, "PASSE", "AB123", STATO_CODE)));

        mockMvc.perform(withAuthHeaders(get(PATH_JSON).param(PARAM_DATE, TEST_DATE),
                        USER_OWNER, ROLE_OWNER, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────── HMAC helpers ──────────────────────────

    /**
     * Adds a fresh, validly-signed set of gateway headers (T-GW-08: a new
     * timestamp and nonce on every call) to the given request builder.
     *
     * @param builder  the request builder to add headers to
     * @param username the value for {@code X-Auth-User}
     * @param role     the value for {@code X-Auth-Role}
     * @param hotelId  the value for {@code X-Auth-Hotel}
     * @return the same builder, with all six gateway headers set
     */
    private static MockHttpServletRequestBuilder withAuthHeaders(
            final MockHttpServletRequestBuilder builder,
            final String username, final String role, final String hotelId) {
        final String timestamp = String.valueOf(System.currentTimeMillis());
        final String nonce = UUID.randomUUID().toString();
        final String signature = hmac(username, role, hotelId, timestamp, nonce);
        return builder
                .header(HDR_USER, username)
                .header(HDR_ROLE, role)
                .header(HDR_HOTEL, hotelId)
                .header(HDR_TIMESTAMP, timestamp)
                .header(HDR_NONCE, nonce)
                .header(HDR_SIG, signature);
    }

    private static String hmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC_FAILED", e);
        }
    }
}
