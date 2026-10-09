package com.hotelpms.internalauth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tells a service whether the current request came from outside (through the API gateway)
 * or from another service over Feign.
 *
 * <p>The gateway sets {@code X-Gateway-Origin} on every request it forwards, overwriting any
 * copy sent by the client; {@link InternalAuthFilter} turns it into the {@link
 * #GATEWAY_CALLER_AUTHORITY} authority. Service-to-service calls do not go through the gateway
 * and {@code InternalFeignAuthInterceptor} never forwards the header, so they never carry it.
 * The marker is deliberately not part of the HMAC payload (that would be a lock-step signature
 * format change): anyone able to reach a service directly already holds the shared secret and is
 * a trusted caller.
 */
public final class CallerContext {

    /** Authority granted when the request was forwarded by the API gateway. */
    public static final String GATEWAY_CALLER_AUTHORITY = "GATEWAY_CALLER";

    private static final String UNKNOWN_USER = "unknown";

    private CallerContext() {
    }

    /**
     * @return {@code true} if the current request was forwarded by the API gateway, i.e. it
     *         comes from a public client rather than from another service
     */
    public static boolean isViaGateway() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (final GrantedAuthority authority : auth.getAuthorities()) {
            if (GATEWAY_CALLER_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the authenticated username with CR/LF replaced (log-injection safe), or
     *         {@code "unknown"} if there is none; meant for audit logs
     */
    public static String username() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null
                ? UNKNOWN_USER
                : auth.getName().replaceAll("[\\r\\n]", "_");
    }
}
