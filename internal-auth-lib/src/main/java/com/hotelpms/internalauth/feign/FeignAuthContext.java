package com.hotelpms.internalauth.feign;

/**
 * Authenticated caller identity used to sign an outgoing Feign call when no
 * inbound HTTP request context is available (e.g. a scheduled batch job).
 *
 * @param user    the username to attribute the outgoing call to
 * @param role    the role associated with {@code user}
 * @param hotelId the hotel UUID the call is scoped to
 */
public record FeignAuthContext(String user, String role, String hotelId) {
}
