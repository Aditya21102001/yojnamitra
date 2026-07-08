package com.yojanamitra.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** A citizen's self-described situation received from the Angular client. */
public record ProfileRequest(
        @Min(0) @Max(120) Integer age,
        String gender,
        String state,
        String occupation,
        @Min(0) Integer annualIncome,
        String category,
        String description
) {
}
