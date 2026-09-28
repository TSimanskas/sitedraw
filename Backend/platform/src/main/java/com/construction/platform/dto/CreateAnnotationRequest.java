package com.construction.platform.dto;

import com.construction.platform.domain.AnnotationType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateAnnotationRequest(
        @NotNull int pageNumber,
        @NotNull AnnotationType type,
        @NotNull Map<String, Object> data
) {
}
