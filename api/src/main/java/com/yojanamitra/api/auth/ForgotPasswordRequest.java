package com.yojanamitra.api.auth;

import jakarta.validation.constraints.NotBlank;

/** Either the username or the email works — we look up both. */
public record ForgotPasswordRequest(@NotBlank String usernameOrEmail) {
}
