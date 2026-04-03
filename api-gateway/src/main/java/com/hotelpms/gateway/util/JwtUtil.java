package com.hotelpms.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Utility class for JWT operations.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:bXktMzItYnl0ZS1zZWNyZXQta2V5LWZvci10ZXN0LWxvY2FsLWRldi0xMjM0NQ==}")
    private String secret;

    private Key key;

    /**
     * Initializes the signing key based on the injected secret.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(this.secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Parses the JWT token and returns all claims.
     *
     * @param token the JWT token as a String
     * @return the claims parsed from the token
     */
    public Claims getAllClaimsFromToken(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
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
