package com.construction.platform.dto;

import com.construction.platform.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @StrongPassword String newPassword
) {
}
