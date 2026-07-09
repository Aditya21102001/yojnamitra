package com.yojanamitra.api.auth;

/**
 * Result of an authentication attempt.
 *
 * <p>Either the password was enough and {@code token} is a full session, or the
 * account has a second factor and {@code mfaToken} must be exchanged, together
 * with a code, at {@code /api/auth/mfa/verify}. The two are mutually exclusive:
 * a challenge response never carries an access token.
 */
public record AuthResponse(String token, String username, boolean mfaRequired, String mfaToken) {

    public static AuthResponse authenticated(String token, String username) {
        return new AuthResponse(token, username, false, null);
    }

    public static AuthResponse mfaChallenge(String mfaToken, String username) {
        return new AuthResponse(null, username, true, mfaToken);
    }
}
