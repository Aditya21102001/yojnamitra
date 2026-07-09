package com.yojanamitra.api.auth;

import jakarta.validation.constraints.NotBlank;

/** Second step of login: the challenge token from /login plus a TOTP or recovery code. */
public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
}
