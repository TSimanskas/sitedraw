package com.construction.platform.dto;

import com.construction.platform.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateProjectRequest(
        @NotBlank String name,
        String siteAddress,
        String clientName,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
}
