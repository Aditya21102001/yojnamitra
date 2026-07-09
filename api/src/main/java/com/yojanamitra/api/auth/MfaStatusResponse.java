package com.yojanamitra.api.auth;

public record MfaStatusResponse(boolean enabled, int recoveryCodesRemaining) {
}
