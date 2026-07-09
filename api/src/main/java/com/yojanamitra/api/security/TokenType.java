package com.yojanamitra.api.security;

/**
 * Distinguishes a full session token from the short-lived token handed out
 * after a correct password but before the second factor is proven.
 *
 * <p>Both are signed with the same key and carry the same subject, so without
 * an explicit type claim a challenge token would be indistinguishable from an
 * access token and could be replayed straight past MFA.
 */
public enum TokenType {

    /** Full session. Accepted by {@link JwtAuthFilter}. */
    ACCESS,

    /** Password verified, second factor pending. Only {@code /api/auth/mfa/verify} accepts it. */
    MFA_CHALLENGE
}
