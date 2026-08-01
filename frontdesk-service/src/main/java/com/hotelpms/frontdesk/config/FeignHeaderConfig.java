package com.hotelpms.frontdesk.config;

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
     * Registers the shared {@link InternalFeignAuthInterceptor}. This service
     * has no calls originating outside an HTTP request context, so the
     * fallback always resolves to empty.
     *
     * @return the configured interceptor
     */
    @Bean
    public RequestInterceptor authHeaderInterceptor() {
        return new InternalFeignAuthInterceptor(hmacSecret, Optional::empty);
    }
}
