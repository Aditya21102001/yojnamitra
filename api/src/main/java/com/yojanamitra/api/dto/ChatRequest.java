package com.yojanamitra.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/chat. */
public record ChatRequest(
        @NotBlank String schemeId,
        @NotBlank String question,
        String lang
) {
}
