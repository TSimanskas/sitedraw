package com.construction.platform.dto;

import com.construction.platform.domain.AnnotationType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record MarkupAnnotationRequest(
        @NotNull int pageNumber,
        @NotNull AnnotationType type,
        @NotNull Map<String, Object> data,
        UUID sourceAnnotationId
) {
}
