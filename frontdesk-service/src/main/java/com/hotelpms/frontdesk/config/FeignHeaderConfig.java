package com.hotelpms.frontdesk.config;

import com.hotelpms.internalauth.feign.FeignAuthContext;
import com.hotelpms.internalauth.feign.InternalFeignAuthInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Feign configuration that signs outgoing calls with the internal HMAC
 * signature so that downstream service {@code InternalAuthFilter} instances
 * accept them (T-GW-07 / T-GST-05). See {@link InternalFeignAuthInterceptor}
 * for the shared signing logic.
 *
 * <p>The scheduled night-audit job also originates a call to billing-service
 * (the cash-closing summary) outside an HTTP request context, so — same
 * pattern as guest-service's GDPR retention job — the fallback sources the
 * auth context from {@link NightAuditJobContext} when no inbound request is
 * bound to the current thread.
 */
@Configuration
public class FeignHeaderConfig {

    private final String hmacSecret;

    /**
     * Constructs the Feign configuration with the shared HMAC secret.
     *
     * @param hmacSecret the internal HMAC secret, shared with all microservices
     */
    public FeignHeaderConfig(@Value("${internal.hmac.secret}") final String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * Registers the shared {@link InternalFeignAuthInterceptor} with a
     * fallback that sources the auth context from {@link NightAuditJobContext}
     * when no inbound request context is bound to the current thread.
     *
     * @return the configured interceptor
     */
    @Bean
    public RequestInterceptor authHeaderInterceptor() {
        return new InternalFeignAuthInterceptor(hmacSecret, FeignHeaderConfig::resolveBatchJobFallback);
    }

    private static Optional<FeignAuthContext> resolveBatchJobFallback() {
        final NightAuditJobContext ctx = NightAuditJobContext.get();
        return ctx == null
                ? Optional.empty()
                : Optional.of(new FeignAuthContext(ctx.getUser(), ctx.getRole(), ctx.getHotelId()));
    }
}
