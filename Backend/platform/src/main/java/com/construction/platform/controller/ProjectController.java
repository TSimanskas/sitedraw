package com.construction.platform.controller;

import com.construction.platform.dto.*;
import com.construction.platform.security.UserPrincipal;
import com.construction.platform.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> listProjects(
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.listProjects(principal, q);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.getProject(projectId, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.createProject(request, principal);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.updateProject(projectId, request, principal);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        projectService.deleteProject(projectId, principal);
    }

    @PostMapping("/{projectId}/members")
    public ProjectResponse assignMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody AssignMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.assignMember(projectId, request, principal);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ProjectResponse removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.removeMember(projectId, userId, principal);
    }

    @GetMapping("/{projectId}/assignable-users")
    public List<UserResponse> listAssignableUsers(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.listAssignableUsers(projectId, principal);
    }
}
