package com.hotelpms.guest.controller;

import com.hotelpms.guest.security.SecurityConfig;
import com.hotelpms.guest.service.GuestService;
import com.hotelpms.internalauth.security.NonceStore;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-level security tests for {@link GuestController}'s
 * GDPR-sensitive endpoints, protected by
 * {@code @PreAuthorize("hasAnyRole('ADMIN','OWNER')")} (round 1 finding #7 /
 * round 2 exploratory audit): {@code DELETE /{id}} (hard-delete/anonymise)
 * and {@code GET /{id}/export} (Art. 20 full data-portability dump) were
 * previously reachable by any authenticated role, including RECEPTIONIST.
 *
 * <p>Modeled on {@code MenuItemControllerSecurityTest} (fb-service):
 * default Spring Security auto-configurations are excluded so only
 * {@link SecurityConfig} processes requests, exercising the real
 * {@code InternalAuthFilter} + {@code @EnableMethodSecurity} AOP path.
 */
@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@WebMvcTest(
        controllers = GuestController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
class GuestControllerSecurityTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TEST_SECRET = "test-hmac-secret-minimum-32-characters-for-unit-tests";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private static final String BASE_URL = "/api/v1/guests";
    private static final String PATH_BY_ID = "/{id}";
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private GuestService guestService;

    @MockitoBean
    private NonceStore nonceStore;

    @BeforeEach
    void stubNonceStoreAsAlwaysFresh() {
        when(nonceStore.claim(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void deleteGuestReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        delete(BASE_URL + PATH_BY_ID, GUEST_ID),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportGuestDataReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/{id}/export", GUEST_ID),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteGuestReturnsNoContentForAdmin() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        delete(BASE_URL + PATH_BY_ID, GUEST_ID),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void exportGuestDataReturnsOkForAdmin() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/{id}/export", GUEST_ID),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void guestByIdRemainsReachableForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + PATH_BY_ID, GUEST_ID),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

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
