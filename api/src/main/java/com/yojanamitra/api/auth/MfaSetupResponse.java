package com.yojanamitra.api.auth;

/**
 * Enrolment material, shown once. {@code qrDataUri} is a self-contained
 * {@code data:image/png;base64,...} string; {@code secret} is the same value in
 * a form users can type when a camera is unavailable.
 */
public record MfaSetupResponse(String secret, String qrDataUri, String otpAuthUri) {
}
