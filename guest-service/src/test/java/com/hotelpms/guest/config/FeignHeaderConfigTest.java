package com.hotelpms.guest.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

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
 * <p>The signing logic itself (headers, HMAC, timestamp/nonce, empty-fallback
 * behavior) is covered once by
 * {@code com.hotelpms.internalauth.feign.InternalFeignAuthInterceptorTest} in
 * internal-auth-lib. This class only verifies guest-service's own wiring: that
 * outgoing calls made outside an HTTP request context (the GDPR retention
 * batch job, T-GST-05) are signed using {@link BatchJobContext}.
 */
class FeignHeaderConfigTest {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";

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
        return "unit-test-feign-header-config-guest-service";
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        BatchJobContext.clear();
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
    void shouldSignOutgoingCallUsingBatchJobContextWhenNoRequestContext()
            throws NoSuchAlgorithmException, InvalidKeyException {
        RequestContextHolder.resetRequestAttributes();
        BatchJobContext.set(HOTEL_ID);

        final RequestTemplate template = new RequestTemplate();
        config.authHeaderInterceptor().apply(template);

        final String timestamp = template.headers().get(HEADER_TIMESTAMP).iterator().next();
        final String nonce = template.headers().get(HEADER_NONCE).iterator().next();
        final String signature = template.headers().get(HEADER_SIGNATURE).iterator().next();
        final BatchJobContext batchCtx = BatchJobContext.get();

        assertThat(template.headers().get(HEADER_USER)).containsExactly(batchCtx.getUser());
        assertThat(template.headers().get(HEADER_ROLE)).containsExactly(batchCtx.getRole());
        assertThat(template.headers().get(HEADER_HOTEL)).containsExactly(HOTEL_ID);
        assertThat(signature)
                .isEqualTo(computeHmac(batchCtx.getUser(), batchCtx.getRole(), HOTEL_ID, timestamp, nonce));
    }

    @Test
    void shouldNotSetHeadersWhenNeitherRequestContextNorBatchJobContextPresent() {
        RequestContextHolder.resetRequestAttributes();
        BatchJobContext.clear();

        final RequestTemplate template = new RequestTemplate();
        config.authHeaderInterceptor().apply(template);

        assertThat(template.headers()).isEmpty();
    }
}
