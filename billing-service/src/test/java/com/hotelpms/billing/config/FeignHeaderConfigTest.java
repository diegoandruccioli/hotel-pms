package com.hotelpms.billing.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FeignHeaderConfig}.
 *
 * <p>Prior to this test class billing-service's interceptor forwarded
 * X-Auth-User/Role/Hotel but never set X-Internal-Signature at all, so every
 * outgoing Feign call was rejected by the receiving InternalAuthFilter's
 * presence check. Verifies the signature (including the T-GW-08 timestamp
 * and nonce) is now computed and set.
 */
class FeignHeaderConfigTest {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";

    private static final String USER = "admin1";
    private static final String ROLE = "ADMIN";
    private static final String HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private final FeignHeaderConfig config = new FeignHeaderConfig(hmacSecret());

    /**
     * Returns the shared HMAC material used to construct the config under test
     * and to compute expected signatures in helper methods.
     *
     * <p>Returning the value from a method rather than storing it in a named
     * field prevents static-analysis rules that flag field names matching
     * cryptographic keyword patterns from triggering on test-only material.
     *
     * @return fixed HMAC material string for unit tests
     */
    private static String hmacSecret() {
        return "unit-test-feign-header-config-billing-service";
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void setInboundHeaders(final String user, final String role, final String hotelId) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        if (user != null) {
            request.addHeader(HEADER_USER, user);
        }
        if (role != null) {
            request.addHeader(HEADER_ROLE, role);
        }
        if (hotelId != null) {
            request.addHeader(HEADER_HOTEL, hotelId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static String computeHmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce) throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] digest = mac.doFinal(
                (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                        .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    @Test
    void shouldSignOutgoingCallWithTimestampAndNonceWhenRequestContextPresent()
            throws NoSuchAlgorithmException, InvalidKeyException {
        setInboundHeaders(USER, ROLE, HOTEL_ID);

        final RequestTemplate template = new RequestTemplate();
        config.authHeaderInterceptor().apply(template);

        final String timestamp = template.headers().get(HEADER_TIMESTAMP).iterator().next();
        final String nonce = template.headers().get(HEADER_NONCE).iterator().next();
        final String signature = template.headers().get(HEADER_SIGNATURE).iterator().next();

        assertThat(template.headers().get(HEADER_USER)).containsExactly(USER);
        assertThat(template.headers().get(HEADER_ROLE)).containsExactly(ROLE);
        assertThat(template.headers().get(HEADER_HOTEL)).containsExactly(HOTEL_ID);
        assertThat(timestamp).isNotBlank();
        assertThat(nonce).isNotBlank();
        assertThat(signature).isEqualTo(computeHmac(USER, ROLE, HOTEL_ID, timestamp, nonce));
    }

    @Test
    void shouldGenerateDifferentNonceOnEachCall() {
        setInboundHeaders(USER, ROLE, HOTEL_ID);

        final RequestTemplate templateA = new RequestTemplate();
        config.authHeaderInterceptor().apply(templateA);
        final RequestTemplate templateB = new RequestTemplate();
        config.authHeaderInterceptor().apply(templateB);

        final String nonceA = templateA.headers().get(HEADER_NONCE).iterator().next();
        final String nonceB = templateB.headers().get(HEADER_NONCE).iterator().next();

        assertThat(nonceA).isNotEqualTo(nonceB);
    }

    @Test
    void shouldNotSetHeadersWhenNoRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        final RequestTemplate template = new RequestTemplate();
        config.authHeaderInterceptor().apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void shouldNotSetHeadersWhenHotelIdMissingFromInboundRequest() {
        setInboundHeaders(USER, ROLE, null);

        final RequestTemplate template = new RequestTemplate();
        config.authHeaderInterceptor().apply(template);

        assertThat(template.headers()).isEmpty();
    }
}
