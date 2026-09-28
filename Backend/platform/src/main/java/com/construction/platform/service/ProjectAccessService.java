package com.construction.platform.service;

import com.construction.platform.domain.Project;
import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.exception.ApiException;
import com.construction.platform.repository.ProjectMemberRepository;
import com.construction.platform.repository.ProjectRepository;
import com.construction.platform.repository.UserRepository;
import com.construction.platform.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectAccessService {

    public static final String NO_PROJECT_ACCESS = "You do not have access to this project";
    public static final String CANNOT_MANAGE_PROJECT = "You do not have permission to manage this project";
    public static final String CANNOT_UPLOAD_DOCUMENTS = "You do not have permission to upload documents to this project";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessService(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    public User requireUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid token"));
    }

    public Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    public boolean hasProjectAccess(Project project, User user) {
        return project != null
                && user != null
                && projectMemberRepository.existsByProjectIdAndUserId(project.getId(), user.getId());
    }

    public boolean canView(Project project, User user, UserRole role) {
        return role == UserRole.DIRECTOR || hasProjectAccess(project, user);
    }

    public boolean canManage(Project project, User user, UserRole role) {
        if (role == UserRole.DIRECTOR) {
            return true;
        }
        return role == UserRole.PROJECT_MANAGER && hasProjectAccess(project, user);
    }

    public void assertCanView(Project project, User user, UserRole role) {
        if (!canView(project, user, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, NO_PROJECT_ACCESS);
        }
    }

    public void assertCanManage(Project project, User user, UserRole role) {
        assertCanManage(project, user, role, CANNOT_MANAGE_PROJECT);
    }

    public void assertCanManage(Project project, User user, UserRole role, String message) {
        if (!canManage(project, user, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, message);
        }
    }
}
