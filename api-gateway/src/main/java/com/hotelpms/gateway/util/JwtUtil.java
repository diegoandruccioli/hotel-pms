package com.hotelpms.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Utility class for JWT operations.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private Key key;

    /**
     * Lazily initializes and returns the signing key.
     *
     * @return the signing key
     * @throws IllegalArgumentException if the secret is null, empty, or not valid Base64
     */
    private Key getSigningKey() {
        if (this.key == null) {
            if (this.secret == null || this.secret.isBlank()) {
                throw new IllegalArgumentException("JWT secret is not configured (null or empty)");
            }
            try {
                byte[] keyBytes = Decoders.BASE64.decode(this.secret);
                this.key = Keys.hmacShaKeyFor(keyBytes);
            } catch (Exception e) {
                // If the secret is literally "${JWT_SECRET}", it means config resolution failed
                if (this.secret.contains("${")) {
                    throw new IllegalArgumentException("JWT secret placeholder was not resolved: " + this.secret, e);
                }
                throw new IllegalArgumentException("Failed to decode JWT secret. Ensure it is a valid Base64 string.", e);
            }
        }
        return this.key;
    }

    /**
     * Parses the JWT token and returns all claims.
     *
     * @param token the JWT token as a String
     * @return the claims parsed from the token
     */
    public Claims getAllClaimsFromToken(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isTokenExpired(final String token) {
        return this.getAllClaimsFromToken(token).getExpiration().before(new Date());
    }

    /**
     * Checks if the given token is invalid (expired).
     *
     * @param token the JWT token to evaluate
     * @return true if token is invalid, false otherwise
     */
    public boolean isInvalid(final String token) {
        return this.isTokenExpired(token);
    }
}
