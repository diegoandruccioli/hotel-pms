package com.hotelpms.internalauth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Canonical unit tests for {@link TenantContext} — the single implementation
 * replacing ~20 per-service copies of this hotelId-extraction logic.
 */
class TenantContextTest {

    private static final String HOTEL_ID_NOT_AVAILABLE = "HOTEL_ID_NOT_AVAILABLE";
    private static final String USER = "user";
    private static final int NOT_A_STRING_DETAILS = 42;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesHotelIdFromAuthenticationDetails() {
        final UUID hotelId = UUID.randomUUID();
        setAuthDetails(hotelId.toString());

        assertThat(TenantContext.resolveHotelId()).isEqualTo(hotelId);
    }

    @Test
    void throwsIllegalStateWhenNoAuthenticationPresent() {
        SecurityContextHolder.clearContext();

        assertThatIllegalStateException()
                .isThrownBy(TenantContext::resolveHotelId)
                .withMessage(HOTEL_ID_NOT_AVAILABLE);
    }

    @Test
    void throwsIllegalStateWhenDetailsAreNull() {
        final var auth = new UsernamePasswordAuthenticationToken(USER, "", List.of());
        auth.setDetails(null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatIllegalStateException()
                .isThrownBy(TenantContext::resolveHotelId)
                .withMessage(HOTEL_ID_NOT_AVAILABLE);
    }

    @Test
    void throwsIllegalStateWhenDetailsAreNotAString() {
        final var auth = new UsernamePasswordAuthenticationToken(USER, "", List.of());
        auth.setDetails(NOT_A_STRING_DETAILS);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatIllegalStateException()
                .isThrownBy(TenantContext::resolveHotelId)
                .withMessage(HOTEL_ID_NOT_AVAILABLE);
    }

    @Test
    void throwsIllegalStateWhenDetailsAreBlank() {
        setAuthDetails("   ");

        assertThatIllegalStateException()
                .isThrownBy(TenantContext::resolveHotelId)
                .withMessage(HOTEL_ID_NOT_AVAILABLE);
    }

    @Test
    void throwsIllegalStateInsteadOfLeakingIllegalArgumentOnMalformedUuid() {
        setAuthDetails("not-a-uuid");

        assertThatIllegalStateException()
                .isThrownBy(TenantContext::resolveHotelId)
                .withMessage(HOTEL_ID_NOT_AVAILABLE)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    private static void setAuthDetails(final String details) {
        final var auth = new UsernamePasswordAuthenticationToken(USER, "", List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
