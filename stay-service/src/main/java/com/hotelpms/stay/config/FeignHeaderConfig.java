package com.hotelpms.stay.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign configuration that propagates gateway authentication headers
 * (X-Auth-User, X-Auth-Role) from the current inbound HTTP request to all
 * outgoing Feign calls. This prevents 401 rejections from downstream services
 * protected by the InternalAuthFilter.
 */
@Configuration
public class FeignHeaderConfig {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";

    /**
     * Registers a RequestInterceptor that extracts the gateway auth headers
     * from the current request context and forwards them on every Feign call.
     *
     * @return the configured RequestInterceptor
     */
    @Bean
    public RequestInterceptor authHeaderInterceptor() {
        return (final RequestTemplate template) -> {
            final ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attrs == null) {
                return;
            }
            final HttpServletRequest request = attrs.getRequest();
            final String user = request.getHeader(HEADER_USER);
            final String role = request.getHeader(HEADER_ROLE);
            if (StringUtils.hasText(user)) {
                template.header(HEADER_USER, user);
            }
            if (StringUtils.hasText(role)) {
                template.header(HEADER_ROLE, role);
            }
        };
    }
}
