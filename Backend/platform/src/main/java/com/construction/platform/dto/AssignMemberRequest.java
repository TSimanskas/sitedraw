package com.construction.platform.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignMemberRequest(
        @NotNull UUID userId
) {
}
