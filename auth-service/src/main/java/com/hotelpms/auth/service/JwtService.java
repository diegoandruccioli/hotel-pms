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
import java.util.UUID;
import java.util.function.Function;

/**
 * Service for JWT generation and validation.
 *
 * <p>Handles both short-lived access tokens and long-lived refresh tokens.
 * Refresh tokens carry a unique {@code jti} claim that enables selective
 * blacklisting on logout or rotation (T-AUTH-04).</p>
 */
@Component
public class JwtService {

    /** Custom claim key that marks a token as a refresh token. */
    private static final String CLAIM_TYPE = "typ";

    /** Value of the {@code typ} claim in refresh tokens. */
    private static final String TYPE_REFRESH = "refresh";

    /** Custom claim key for the tenant hotel identifier. */
    private static final String CLAIM_HOTEL_ID = "hotelId";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Generates a short-lived access JWT for the given username, role, and hotel.
     *
     * @param username the user's username
     * @param role     the user's role
     * @param hotelId  the tenant identifier for multi-hotel isolation
     * @return the generated access JWT
     */
    public String generateToken(final String username, final Role role, final UUID hotelId) {
        final Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        claims.put(CLAIM_HOTEL_ID, hotelId.toString());
        return buildToken(claims, username, jwtExpiration);
    }

    /**
     * Generates a long-lived refresh JWT for the given username, role, and hotel.
     *
     * <p>The token includes a unique {@code jti} (JWT ID) and a
     * {@code typ=refresh} marker to distinguish it from access tokens.
     * The JTI can be blacklisted on logout or on rotation (T-AUTH-04).</p>
     *
     * @param username the user's username
     * @param role     the user's role
     * @param hotelId  the tenant identifier for multi-hotel isolation
     * @return the generated refresh JWT
     */
    public String generateRefreshToken(final String username, final Role role, final UUID hotelId) {
        final Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        claims.put(CLAIM_HOTEL_ID, hotelId.toString());
        claims.put(CLAIM_TYPE, TYPE_REFRESH);
        return buildRefreshToken(claims, username, refreshExpiration, UUID.randomUUID().toString());
    }

    /**
     * Validates an access JWT token against the given username.
     *
     * @param token    the JWT token
     * @param username the username to validate against
     * @return {@code true} if the token is valid and not expired
     */
    public boolean isTokenValid(final String token, final String username) {
        final String tokenUsername = extractUsername(token);
        return tokenUsername.equals(username) && !isTokenExpired(token);
    }

    /**
     * Returns {@code true} if the token carries a {@code typ=refresh} claim.
     *
     * @param token the JWT
     * @return {@code true} if this is a refresh token
     */
    public boolean isRefreshToken(final String token) {
        return TYPE_REFRESH.equals(extractClaim(token, c -> c.get(CLAIM_TYPE, String.class)));
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
     * Extracts the JTI (JWT ID) claim from the given token.
     *
     * @param token the JWT
     * @return the JTI value, or {@code null} if absent
     */
    public String extractJti(final String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Extracts the {@code hotelId} claim from the given token.
     *
     * @param token the JWT
     * @return the hotel UUID, or {@code null} if absent
     */
    public UUID extractHotelId(final String token) {
        final String raw = extractClaim(token, c -> c.get(CLAIM_HOTEL_ID, String.class));
        return raw != null ? UUID.fromString(raw) : null;
    }

    /**
     * Extracts the expiration as an {@link Instant} from the given token.
     *
     * @param token the JWT
     * @return the expiration {@link Instant}
     */
    public Instant extractExpirationInstant(final String token) {
        return extractClaim(token, c -> c.getExpiration().toInstant());
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

    private boolean isTokenExpired(final String token) {
        return extractClaim(token, Claims::getExpiration).before(Date.from(Instant.now()));
    }

    private String buildToken(final Map<String, Object> extraClaims, final String subject,
            final long expiration) {
        final Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildRefreshToken(final Map<String, Object> extraClaims, final String subject,
            final long expiration, final String jti) {
        final Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setId(jti)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
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
