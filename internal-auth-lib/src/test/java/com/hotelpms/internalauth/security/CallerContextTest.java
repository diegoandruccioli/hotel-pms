package com.hotelpms.internalauth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallerContextTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isNotViaGatewayWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(CallerContext.isViaGateway()).isFalse();
    }

    @Test
    void isNotViaGatewayWithoutTheMarkerAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "", List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))));

        assertThat(CallerContext.isViaGateway()).isFalse();
    }

    @Test
    void isViaGatewayWithTheMarkerAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "", List.of(
                        new SimpleGrantedAuthority("ROLE_RECEPTIONIST"),
                        new SimpleGrantedAuthority(CallerContext.GATEWAY_CALLER_AUTHORITY))));

        assertThat(CallerContext.isViaGateway()).isTrue();
    }

    @Test
    void usernameIsTheAuthenticationNameOrUnknown() {
        assertThat(CallerContext.username()).isEqualTo("unknown");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "recept1", "", List.of()));

        assertThat(CallerContext.username()).isEqualTo("recept1");
    }

    @Test
    void usernameNeverCarriesLineBreaks() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bob\r\nINFO forged entry", "", List.of()));

        assertThat(CallerContext.username()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void usernameNeverCarriesUnicodeLineSeparators() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bob\u2028forged\u0085entry\u2029", "", List.of()));

        assertThat(CallerContext.username()).doesNotContain("\u2028").doesNotContain("\u0085").doesNotContain("\u2029");
    }
}
