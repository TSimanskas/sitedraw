package com.construction.platform.service;

import com.construction.platform.domain.Project;
import com.construction.platform.domain.ProjectMember;
import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.dto.*;
import com.construction.platform.exception.ApiException;
import com.construction.platform.repository.ProjectMemberRepository;
import com.construction.platform.repository.ProjectRepository;
import com.construction.platform.repository.UserRepository;
import com.construction.platform.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            ProjectAccessService projectAccessService
    ) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UserPrincipal principal, String query) {
        User user = projectAccessService.requireUser(principal);

        List<Project> projects = principal.getRole() == UserRole.DIRECTOR
                ? projectRepository.findAllByOrderByUpdatedAtDesc()
                : projectRepository.findAccessibleByUser(user);

        String needle = query == null || query.isBlank() ? null : query.trim().toLowerCase();

        return projects.stream()
                .filter(project -> matchesProject(project, needle))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UserPrincipal principal) {
        User user = projectAccessService.requireUser(principal);
        Project project = projectAccessService.requireProject(projectId);
        projectAccessService.assertCanView(project, user, principal.getRole());
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UserPrincipal principal) {
        if (principal.getRole() == UserRole.SITE_WORKER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Site workers cannot create projects");
        }

        User creator = projectAccessService.requireUser(principal);
        Project project = new Project(
                request.name().trim(),
                trimToNull(request.siteAddress()),
                trimToNull(request.clientName()),
                creator
        );
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());

        Project saved = projectRepository.save(project);
        addMemberIfMissing(saved, creator);

        return toResponse(saved);
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UserPrincipal principal) {
        User user = projectAccessService.requireUser(principal);
        Project project = projectAccessService.requireProject(projectId);
        projectAccessService.assertCanManage(project, user, principal.getRole());

        project.setName(request.name().trim());
        project.setSiteAddress(trimToNull(request.siteAddress()));
        project.setClientName(trimToNull(request.clientName()));
        project.setStatus(request.status());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID projectId, UserPrincipal principal) {
        if (principal.getRole() != UserRole.DIRECTOR) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only directors can delete projects");
        }

        Project project = projectAccessService.requireProject(projectId);
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectResponse assignMember(UUID projectId, AssignMemberRequest request, UserPrincipal principal) {
        User actor = projectAccessService.requireUser(principal);
        Project project = projectAccessService.requireProject(projectId);
        projectAccessService.assertCanManage(project, actor, principal.getRole());

        User assignee = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (!assignee.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This account is inactive. Reactivate them on the Team page, then add them to the project.");
        }

        if (assignee.getRole() == UserRole.DIRECTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Directors cannot be assigned to projects");
        }

        if (principal.getRole() == UserRole.PROJECT_MANAGER && assignee.getRole() == UserRole.PROJECT_MANAGER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project managers can only assign site workers");
        }

        addMemberIfMissing(project, assignee);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse removeMember(UUID projectId, UUID userId, UserPrincipal principal) {
        User actor = projectAccessService.requireUser(principal);
        Project project = projectAccessService.requireProject(projectId);
        projectAccessService.assertCanManage(project, actor, principal.getRole());

        if (actor.getId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot remove yourself from a project");
        }

        User member = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User is not a member of this project");
        }

        if (principal.getRole() == UserRole.PROJECT_MANAGER && member.getRole() != UserRole.SITE_WORKER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project managers can only remove site workers");
        }

        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
        return toResponse(project);
    }

    private void addMemberIfMissing(Project project, User user) {
        if (projectMemberRepository.existsByProjectIdAndUserId(project.getId(), user.getId())) {
            return;
        }
        projectMemberRepository.save(new ProjectMember(project, user));
    }

    private boolean matchesProject(Project project, String needle) {
        if (needle == null) {
            return true;
        }
        return contains(project.getName(), needle)
                || contains(project.getSiteAddress(), needle)
                || contains(project.getClientName(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProjectResponse toResponse(Project project) {
        List<ProjectMemberResponse> members = projectMemberRepository
                .findByProjectIdOrderByAssignedAtAsc(project.getId())
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
        return ProjectResponse.from(project, members);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAssignableUsers(UUID projectId, UserPrincipal principal) {
        User actor = projectAccessService.requireUser(principal);
        Project project = projectAccessService.requireProject(projectId);
        projectAccessService.assertCanManage(project, actor, principal.getRole());

        Set<UUID> memberIds = projectMemberRepository
                .findByProjectIdOrderByAssignedAtAsc(projectId)
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        List<UserRole> allowedRoles = principal.getRole() == UserRole.DIRECTOR
                ? List.of(UserRole.PROJECT_MANAGER, UserRole.SITE_WORKER)
                : List.of(UserRole.SITE_WORKER);

        return userRepository.findByRoleInOrderByFullNameAsc(allowedRoles).stream()
                .filter(u -> !memberIds.contains(u.getId()))
                .map(UserResponse::from)
                .toList();
    }
}
