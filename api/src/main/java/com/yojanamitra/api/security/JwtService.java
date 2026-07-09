package com.yojanamitra.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Issues and validates HS256 JWTs, tagged with a {@link TokenType}. */
@Service
public class JwtService {

    private static final String TYPE_CLAIM = "typ";

    private final SecretKey key;
    private final long expirationMs;
    private final long mfaChallengeMs;

    public JwtService(
            @Value("${yojanamitra.jwt.secret}") String secret,
            @Value("${yojanamitra.jwt.expiration-ms}") long expirationMs,
            @Value("${yojanamitra.jwt.mfa-challenge-ms}") long mfaChallengeMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.mfaChallengeMs = mfaChallengeMs;
    }

    /** A full session token. */
    public String generateAccess(String username) {
        return generate(username, TokenType.ACCESS, expirationMs);
    }

    /** Short-lived proof that the password was correct; useless on its own. */
    public String generateMfaChallenge(String username) {
        return generate(username, TokenType.MFA_CHALLENGE, mfaChallengeMs);
    }

    private String generate(String username, TokenType type, long ttlMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(TYPE_CLAIM, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the username (subject) only if the token is valid, unexpired, and
     * of exactly {@code expected} type. Throws {@link JwtException} otherwise.
     *
     * <p>Callers must always pass the type they require. A valid signature alone
     * is not enough: an MFA challenge token is signed with the same key as an
     * access token, so accepting either here would let a caller who knows only
     * the password skip the second factor entirely.
     */
    public String extractUsername(String token, TokenType expected) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String actual = claims.get(TYPE_CLAIM, String.class);
        if (!expected.name().equals(actual)) {
            throw new JwtException("Expected a " + expected + " token but got " + actual);
        }
        return claims.getSubject();
    }
}
