package com.construction.platform.dto;

import com.construction.platform.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotNull UserRole role,
        @NotNull Boolean active
) {
}
