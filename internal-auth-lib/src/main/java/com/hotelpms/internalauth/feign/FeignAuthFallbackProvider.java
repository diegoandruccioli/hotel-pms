package com.hotelpms.internalauth.feign;

import java.util.Optional;

/**
 * Resolves the auth context to sign an outgoing Feign call with when no
 * inbound HTTP request context is bound to the current thread.
 *
 * <p>Services with no calls originating outside an HTTP request (the
 * majority) supply {@code Optional::empty}.
 */
@FunctionalInterface
public interface FeignAuthFallbackProvider {

    /**
     * Resolves the fallback auth context for the current thread, if any.
     *
     * @return the auth context to sign with, or empty if none applies
     */
    Optional<FeignAuthContext> resolve();
}
