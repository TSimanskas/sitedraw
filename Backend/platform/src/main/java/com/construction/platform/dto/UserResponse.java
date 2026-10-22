package com.construction.platform.dto;

import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        boolean active,
        @JsonInclude(JsonInclude.Include.NON_NULL) String knownPassword
) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, String knownPassword) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                knownPassword
        );
    }
}
