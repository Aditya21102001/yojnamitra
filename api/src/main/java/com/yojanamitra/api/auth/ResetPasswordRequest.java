package com.yojanamitra.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code code} is required only when the account has two-factor authentication on. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 6, max = 100) String newPassword,
        String code
) {
}
