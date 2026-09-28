package com.construction.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CalibrationRequest(
        @NotNull @Positive Double pixelsPerUnit,
        @NotBlank String unitLabel
) {
}
