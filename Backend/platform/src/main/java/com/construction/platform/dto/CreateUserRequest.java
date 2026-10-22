package com.construction.platform.dto;

import com.construction.platform.domain.UserRole;
import com.construction.platform.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @StrongPassword String password,
        @NotBlank String fullName,
        @NotNull UserRole role
) {
}
