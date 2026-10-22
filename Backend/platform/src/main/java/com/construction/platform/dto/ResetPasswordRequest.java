package com.construction.platform.dto;

import com.construction.platform.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank @StrongPassword String password
) {
}
