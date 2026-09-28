package com.construction.platform.dto;

import com.construction.platform.domain.ProjectMember;
import com.construction.platform.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID userId,
        String fullName,
        String email,
        UserRole role,
        Instant assignedAt
) {
    public static ProjectMemberResponse from(ProjectMember member){
        var user = member.getUser();
        return new ProjectMemberResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                member.getAssignedAt()
        );
    }
}
