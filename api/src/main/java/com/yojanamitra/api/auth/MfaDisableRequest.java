package com.yojanamitra.api.auth;

import jakarta.validation.constraints.NotBlank;

/** Turning MFA off re-proves both factors, so a hijacked session cannot do it alone. */
public record MfaDisableRequest(@NotBlank String password, @NotBlank String code) {
}
