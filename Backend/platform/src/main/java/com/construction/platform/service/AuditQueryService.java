package com.construction.platform.service;

import com.construction.platform.domain.Project;
import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.dto.AuditEventResponse;
import com.construction.platform.exception.ApiException;
import com.construction.platform.repository.AuditEventRepository;
import com.construction.platform.repository.ProjectRepository;
import com.construction.platform.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;

    public AuditQueryService(
            AuditEventRepository auditEventRepository,
            ProjectRepository projectRepository,
            ProjectAccessService projectAccessService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> listAuditEvents(
            UUID projectId,
            UUID documentVersionId,
            UserPrincipal principal
    ) {
        User user = projectAccessService.requireUser(principal);

        if (documentVersionId != null) {
            return auditEventRepository.findByDocumentVersionIdOrderByCreatedAtDesc(documentVersionId).stream()
                    .filter(event -> canViewEvent(event.getProject(), event.getUser(), user, principal.getRole()))
                    .map(AuditEventResponse::from)
                    .toList();
        }

        if (projectId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "projectId or documentVersionId is required");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
        projectAccessService.assertCanView(project, user, principal.getRole());

        return auditEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(event -> canViewEvent(project, event.getUser(), user, principal.getRole()))
                .map(AuditEventResponse::from)
                .toList();
    }

    private boolean canViewEvent(Project project, User eventUser, User viewer, UserRole role) {
        if (role == UserRole.DIRECTOR) {
            return true;
        }
        if (role == UserRole.PROJECT_MANAGER) {
            return projectAccessService.hasProjectAccess(project, viewer);
        }
        return eventUser != null && eventUser.getId().equals(viewer.getId());
    }
}
