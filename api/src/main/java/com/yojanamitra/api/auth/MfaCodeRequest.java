package com.yojanamitra.api.auth;

import jakarta.validation.constraints.NotBlank;

/** A code from the authenticator app, used to confirm enrolment. */
public record MfaCodeRequest(@NotBlank String code) {
}
