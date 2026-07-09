package com.yojanamitra.api.auth;

/** Tells the reset page whether the link still works and whether to ask for a 2FA code. */
public record ResetPrecheckResponse(boolean valid, boolean mfaRequired) {
}
