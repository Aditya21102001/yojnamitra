package com.yojanamitra.api.saved;

import jakarta.validation.constraints.NotBlank;

public record SaveSchemeRequest(
        @NotBlank String schemeId,
        String name,
        String category,
        String verdict,
        String reason,
        String applyUrl
) {
}
