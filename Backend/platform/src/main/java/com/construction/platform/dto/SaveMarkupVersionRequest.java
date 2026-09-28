package com.construction.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveMarkupVersionRequest(
        @NotNull List<@Valid MarkupAnnotationRequest> annotations
) {
}
