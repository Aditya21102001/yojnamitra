package com.yojanamitra.api.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(
            "test-secret-that-is-at-least-32-bytes-long!!", 60_000L, 60_000L);

    @Test
    void accessTokenIsAcceptedAsAccess() {
        String token = jwt.generateAccess("alice");
        assertEquals("alice", jwt.extractUsername(token, TokenType.ACCESS));
    }

    /** The whole point of the type claim: a half-authenticated token is not a session. */
    @Test
    void mfaChallengeTokenIsRejectedWhereAccessIsRequired() {
        String challenge = jwt.generateMfaChallenge("alice");
        assertThrows(JwtException.class, () -> jwt.extractUsername(challenge, TokenType.ACCESS));
    }

    @Test
    void accessTokenIsRejectedAtTheMfaVerifyStep() {
        String access = jwt.generateAccess("alice");
        assertThrows(JwtException.class, () -> jwt.extractUsername(access, TokenType.MFA_CHALLENGE));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-key-32-bytes+", 60_000L, 60_000L);
        String foreign = other.generateAccess("alice");
        assertThrows(JwtException.class, () -> jwt.extractUsername(foreign, TokenType.ACCESS));
    }
}
