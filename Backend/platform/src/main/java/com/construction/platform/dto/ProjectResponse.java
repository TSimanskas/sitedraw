package com.construction.platform.dto;

import com.construction.platform.domain.Project;
import com.construction.platform.domain.ProjectStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String siteAddress,
        String clientName,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdById,
        String createdByName,
        List<ProjectMemberResponse> members
) {

    public static ProjectResponse from(Project project, List<ProjectMemberResponse> members) {
        var creator = project.getCreatedBy();
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getSiteAddress(),
                project.getClientName(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate(),
                creator != null ? creator.getId() : null,
                creator != null ? creator.getFullName() : null,
                members
        );
    }
}
