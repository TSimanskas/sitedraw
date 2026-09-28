package com.construction.platform.dto;

import java.util.UUID;

public record CalibrationResponse(
        UUID documentVersionId,
        Double pixelsPerUnit,
        String unitLabel
) {
}
