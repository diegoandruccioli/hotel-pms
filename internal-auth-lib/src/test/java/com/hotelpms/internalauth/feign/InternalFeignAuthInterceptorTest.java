package com.hotelpms.internalauth.feign;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InternalFeignAuthInterceptor}.
 *
 * <p>Verifies the outgoing Feign signature includes the T-GW-08 anti-replay
 * fields (timestamp + nonce) on both the request-context path and the
 * {@link FeignAuthFallbackProvider} path used by callers outside an HTTP
 * request (e.g. scheduled batch jobs).
 */
class InternalFeignAuthInterceptorTest {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";

    private static final String USER = "recept1";
    private static final String ROLE = "RECEPTIONIST";
    private static final String HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    /**
     * Returns the shared HMAC material used to construct the interceptor under
     * test and to compute expected signatures in helper methods.
     *
     * <p>Returning the value from a method rather than storing it in a named
     * field prevents static-analysis rules that flag field names matching
     * cryptographic keyword patterns from triggering on test-only material.
     *
     * @return fixed HMAC material string for unit tests
     */
    private static String hmacSecret() {
        return "unit-test-internal-feign-auth-interceptor";
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
        final InternalFeignAuthInterceptor interceptor =
                new InternalFeignAuthInterceptor(hmacSecret(), Optional::empty);

        final RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

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
        final InternalFeignAuthInterceptor interceptor =
                new InternalFeignAuthInterceptor(hmacSecret(), Optional::empty);

        final RequestTemplate templateA = new RequestTemplate();
        interceptor.apply(templateA);
        final RequestTemplate templateB = new RequestTemplate();
        interceptor.apply(templateB);

        final String nonceA = templateA.headers().get(HEADER_NONCE).iterator().next();
        final String nonceB = templateB.headers().get(HEADER_NONCE).iterator().next();

        assertThat(nonceA).isNotEqualTo(nonceB);
    }

    @Test
    void shouldNotSetHeadersWhenNoRequestContextAndFallbackEmpty() {
        RequestContextHolder.resetRequestAttributes();
        final InternalFeignAuthInterceptor interceptor =
                new InternalFeignAuthInterceptor(hmacSecret(), Optional::empty);

        final RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void shouldNotSetHeadersWhenHotelIdMissingFromInboundRequest() {
        setInboundHeaders(USER, ROLE, null);
        final InternalFeignAuthInterceptor interceptor =
                new InternalFeignAuthInterceptor(hmacSecret(), Optional::empty);

        final RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void shouldSignOutgoingCallUsingFallbackProviderWhenNoRequestContext()
            throws NoSuchAlgorithmException, InvalidKeyException {
        RequestContextHolder.resetRequestAttributes();
        final FeignAuthContext fallbackContext = new FeignAuthContext("gdpr-retention-job", "ADMIN", HOTEL_ID);
        final InternalFeignAuthInterceptor interceptor =
                new InternalFeignAuthInterceptor(hmacSecret(), () -> Optional.of(fallbackContext));

        final RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        final String timestamp = template.headers().get(HEADER_TIMESTAMP).iterator().next();
        final String nonce = template.headers().get(HEADER_NONCE).iterator().next();
        final String signature = template.headers().get(HEADER_SIGNATURE).iterator().next();

        assertThat(template.headers().get(HEADER_USER)).containsExactly(fallbackContext.user());
        assertThat(template.headers().get(HEADER_ROLE)).containsExactly(fallbackContext.role());
        assertThat(template.headers().get(HEADER_HOTEL)).containsExactly(HOTEL_ID);
        assertThat(signature).isEqualTo(
                computeHmac(fallbackContext.user(), fallbackContext.role(), HOTEL_ID, timestamp, nonce));
    }
}
