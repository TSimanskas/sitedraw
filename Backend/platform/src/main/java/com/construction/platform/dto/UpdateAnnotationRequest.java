package com.construction.platform.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateAnnotationRequest(
        @NotNull Map<String, Object> data
) {
}
