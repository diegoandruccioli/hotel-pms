package com.hotelpms.internalauth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Reads the hotelId that {@link InternalAuthFilter} stores in
 * {@link Authentication#getDetails()} for every authenticated request.
 *
 * <p>Extracted (Point 7 item 3, tenant-isolation audit,
 * {@code backup/DECISIONS.md} ADR-004) from roughly twenty near-identical
 * per-class {@code resolveHotelId()}/{@code extractHotelId()} copies spread
 * across every service. They had quietly drifted apart: about half never
 * checked {@link SecurityContextHolder#getContext()}'s authentication for
 * {@code null} before dereferencing it (a latent NPE, unreachable only
 * because {@code InternalAuthFilter} always runs first in practice), some
 * skipped the blank-string check, and the exception thrown on failure varied
 * by class ({@code IllegalStateException} with three different messages, or
 * a raw {@code IllegalArgumentException} straight out of
 * {@code UUID.fromString} on malformed input). One implementation, one
 * failure mode, tested once instead of copied ~20 times.
 */
public final class TenantContext {

    private static final String HOTEL_ID_NOT_AVAILABLE = "HOTEL_ID_NOT_AVAILABLE";

    private TenantContext() {
    }

    /**
     * @return the authenticated caller's hotelId
     * @throws IllegalStateException if there is no authentication, or its
     *         details are not a valid, non-blank hotelId — both indicate
     *         {@code InternalAuthFilter} was bypassed, which should never
     *         happen for a request that reached this code
     */
    public static UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final Object details = auth == null ? null : auth.getDetails();
        if (!(details instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException(HOTEL_ID_NOT_AVAILABLE);
        }
        try {
            return UUID.fromString(hotelIdStr);
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException(HOTEL_ID_NOT_AVAILABLE, e);
        }
    }
}
