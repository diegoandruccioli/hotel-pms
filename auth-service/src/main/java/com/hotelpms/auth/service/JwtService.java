package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for JWT generation and validation.
 */
@Component
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Generates a JWT token for the given username and role.
     *
     * @param username the user's username
     * @param role     the user's role
     * @return the generated JWT token
     */
    public String generateToken(final String username, final Role role) {
        final Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        return buildToken(claims, username, jwtExpiration);
    }

    private String buildToken(final Map<String, Object> extraClaims, final String subject, final long expiration) {
        final Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates a JWT token.
     *
     * @param token    the JWT token
     * @param username the username to validate against
     * @return true if the token is valid, false otherwise
     */
    public boolean isTokenValid(final String token, final String username) {
        final String tokenUsername = extractUsername(token);
        return tokenUsername.equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(final String token) {
        return extractExpiration(token).before(Date.from(Instant.now()));
    }

    private Date extractExpiration(final String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts the username from the JWT token.
     *
     * @param token the JWT token
     * @return the extracted username
     */
    public String extractUsername(final String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from the JWT token.
     *
     * @param token          the JWT token
     * @param claimsResolver a function to resolve the claim
     * @param <T>            the type of the claim
     * @return the extracted claim
     */
    public <T> T extractClaim(final String token, final Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        final byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
