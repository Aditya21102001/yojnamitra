package com.yojanamitra.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Body of POST /api/match. */
public record MatchRequest(
        @NotNull @Valid ProfileRequest profile,
        Integer topK,
        String lang
) {
}
