package com.construction.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateProjectRequest(
        @NotBlank String name,
        String siteAddress,
        String clientName,
        LocalDate startDate,
        LocalDate endDate
) {
}
