package com.construction.platform.service;

import com.construction.platform.domain.*;
import com.construction.platform.dto.*;
import com.construction.platform.exception.ApiException;
import com.construction.platform.repository.*;
import com.construction.platform.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final AnnotationRevisionRepository annotationRevisionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditService auditService;

    public AnnotationService(
            AnnotationRepository annotationRepository,
            AnnotationRevisionRepository annotationRevisionRepository,
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            ProjectAccessService projectAccessService,
            AuditService auditService
    ) {
        this.annotationRepository = annotationRepository;
        this.annotationRevisionRepository = annotationRevisionRepository;
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.projectAccessService = projectAccessService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AnnotationResponse> listAnnotations(
            UUID documentId,
            int versionNumber,
            Integer pageNumber,
            UserPrincipal principal
    ) {
        DocumentVersion version = requireDocumentVersion(documentId, versionNumber);
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(version.getDocument().getProject(), user, principal.getRole());

        List<Annotation> annotations = pageNumber != null
                ? annotationRepository.findByDocumentVersionAndPageNumberAndDeletedFalseOrderByCreatedAtAsc(version, pageNumber)
                : annotationRepository.findByDocumentVersionAndDeletedFalseOrderByCreatedAtAsc(version);

        return annotations.stream().map(AnnotationResponse::from).toList();
    }

    @Transactional
    public AnnotationResponse createAnnotation(
            UUID documentId,
            int versionNumber,
            CreateAnnotationRequest request,
            UserPrincipal principal,
            String ipAddress
    ) {
        DocumentVersion version = requireDocumentVersion(documentId, versionNumber);
        User user = projectAccessService.requireUser(principal);
        Project project = version.getDocument().getProject();
        projectAccessService.assertCanView(project, user, principal.getRole());

        if (request.pageNumber() < 1 || request.pageNumber() > version.getPageCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid page number");
        }

        Annotation annotation = annotationRepository.save(new Annotation(
                version,
                request.pageNumber(),
                request.type(),
                new HashMap<>(request.data()),
                user
        ));

        recordRevision(annotation, RevisionAction.CREATE, null, snapshot(annotation), user);

        auditService.log(
                user,
                AuditAction.ANNOTATION_CREATED,
                "Annotation",
                annotation.getId(),
                project,
                version.getId(),
                null,
                snapshot(annotation),
                ipAddress
        );

        return AnnotationResponse.from(annotation);
    }

    @Transactional
    public AnnotationResponse createMarkupAnnotation(
            UUID documentId,
            int versionNumber,
            DocumentVersion sourceVersion,
            MarkupAnnotationRequest request,
            UserPrincipal principal,
            String ipAddress
    ) {
        DocumentVersion version = requireDocumentVersion(documentId, versionNumber);
        User actor = projectAccessService.requireUser(principal);
        Project project = version.getDocument().getProject();
        projectAccessService.assertCanView(project, actor, principal.getRole());

        if (request.pageNumber() < 1 || request.pageNumber() > version.getPageCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid page number");
        }

        User author = resolveCopiedAuthor(sourceVersion, request.sourceAnnotationId(), actor);

        Annotation annotation = annotationRepository.save(new Annotation(
                version,
                request.pageNumber(),
                request.type(),
                new HashMap<>(request.data()),
                author
        ));

        recordRevision(annotation, RevisionAction.CREATE, null, snapshot(annotation), actor);

        auditService.log(
                actor,
                AuditAction.ANNOTATION_CREATED,
                "Annotation",
                annotation.getId(),
                project,
                version.getId(),
                null,
                snapshot(annotation),
                ipAddress
        );

        return AnnotationResponse.from(annotation);
    }

    private User resolveCopiedAuthor(DocumentVersion sourceVersion, UUID sourceAnnotationId, User fallback) {
        if (sourceAnnotationId == null) {
            return fallback;
        }

        return annotationRepository.findById(sourceAnnotationId)
                .filter(annotation -> !annotation.isDeleted())
                .filter(annotation -> annotation.getDocumentVersion().getId().equals(sourceVersion.getId()))
                .map(Annotation::getCreatedBy)
                .orElse(fallback);
    }

    @Transactional
    public AnnotationResponse updateAnnotation(
            UUID annotationId,
            UpdateAnnotationRequest request,
            UserPrincipal principal,
            String ipAddress
    ) {
        Annotation annotation = requireActiveAnnotation(annotationId);
        User user = projectAccessService.requireUser(principal);
        Project project = annotation.getDocumentVersion().getDocument().getProject();
        assertCanEditAnnotation(annotation, project, user, principal.getRole());

        Map<String, Object> beforeState = snapshot(annotation);
        annotation.setData(new HashMap<>(request.data()));
        Annotation saved = annotationRepository.save(annotation);

        recordRevision(saved, RevisionAction.UPDATE, beforeState, snapshot(saved), user);

        auditService.log(
                user,
                AuditAction.ANNOTATION_UPDATED,
                "Annotation",
                saved.getId(),
                project,
                saved.getDocumentVersion().getId(),
                beforeState,
                snapshot(saved),
                ipAddress
        );

        return AnnotationResponse.from(saved);
    }

    @Transactional
    public void deleteAnnotation(UUID annotationId, UserPrincipal principal, String ipAddress) {
        Annotation annotation = requireActiveAnnotation(annotationId);
        User user = projectAccessService.requireUser(principal);
        Project project = annotation.getDocumentVersion().getDocument().getProject();
        assertCanEditAnnotation(annotation, project, user, principal.getRole());

        Map<String, Object> beforeState = snapshot(annotation);
        annotation.setDeleted(true);
        annotation.setDeletedAt(Instant.now());
        annotationRepository.save(annotation);

        recordRevision(annotation, RevisionAction.DELETE, beforeState, null, user);

        auditService.log(
                user,
                AuditAction.ANNOTATION_DELETED,
                "Annotation",
                annotation.getId(),
                project,
                annotation.getDocumentVersion().getId(),
                beforeState,
                null,
                ipAddress
        );
    }

    @Transactional(readOnly = true)
    public List<AnnotationRevisionResponse> listRevisions(UUID annotationId, UserPrincipal principal) {
        Annotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Annotation not found"));
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(annotation.getDocumentVersion().getDocument().getProject(), user, principal.getRole());

        return annotationRevisionRepository.findByAnnotationIdOrderByChangedAtDesc(annotationId).stream()
                .map(AnnotationRevisionResponse::from)
                .toList();
    }

    @Transactional
    public AnnotationResponse rollbackAnnotation(
            UUID annotationId,
            RollbackRequest request,
            UserPrincipal principal,
            String ipAddress
    ) {
        Annotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Annotation not found"));

        User user = projectAccessService.requireUser(principal);
        Project project = annotation.getDocumentVersion().getDocument().getProject();
        assertCanRollback(annotation, project, user, principal.getRole());

        AnnotationRevision targetRevision = resolveRollbackTarget(annotationId, request);

        Map<String, Object> beforeState = snapshot(annotation);
        applyRollback(annotation, targetRevision);
        Annotation saved = annotationRepository.save(annotation);

        recordRevision(saved, RevisionAction.ROLLBACK, beforeState, snapshot(saved), user);

        auditService.log(
                user,
                AuditAction.ANNOTATION_ROLLBACK,
                "Annotation",
                saved.getId(),
                project,
                saved.getDocumentVersion().getId(),
                beforeState,
                snapshot(saved),
                ipAddress
        );

        return AnnotationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CalibrationResponse getCalibration(UUID documentId, int versionNumber, UserPrincipal principal) {
        DocumentVersion version = requireDocumentVersion(documentId, versionNumber);
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(version.getDocument().getProject(), user, principal.getRole());

        return new CalibrationResponse(
                version.getId(),
                version.getCalibrationPixelsPerUnit(),
                version.getCalibrationUnitLabel()
        );
    }

    @Transactional
    public CalibrationResponse updateCalibration(
            UUID documentId,
            int versionNumber,
            CalibrationRequest request,
            UserPrincipal principal
    ) {
        DocumentVersion version = requireDocumentVersion(documentId, versionNumber);
        User user = projectAccessService.requireUser(principal);
        Project project = version.getDocument().getProject();

        if (principal.getRole() == UserRole.SITE_WORKER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Site workers cannot update scale calibration");
        }
        projectAccessService.assertCanManage(project, user, principal.getRole());

        version.setCalibrationPixelsPerUnit(request.pixelsPerUnit());
        version.setCalibrationUnitLabel(request.unitLabel().trim());
        documentVersionRepository.save(version);

        return new CalibrationResponse(
                version.getId(),
                version.getCalibrationPixelsPerUnit(),
                version.getCalibrationUnitLabel()
        );
    }

    private AnnotationRevision resolveRollbackTarget(UUID annotationId, RollbackRequest request) {
        if (request != null && request.revisionId() != null) {
            return annotationRevisionRepository.findById(request.revisionId())
                    .filter(revision -> revision.getAnnotation().getId().equals(annotationId))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Revision not found"));
        }

        return annotationRevisionRepository.findFirstByAnnotationIdOrderByChangedAtDesc(annotationId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No revision history to roll back"));
    }

    private void applyRollback(Annotation annotation, AnnotationRevision revision) {
        switch (revision.getAction()) {
            case CREATE -> {
                annotation.setDeleted(true);
                annotation.setDeletedAt(Instant.now());
            }
            case UPDATE, ROLLBACK -> restoreAnnotationState(annotation, revision.getBeforeState());
            case DELETE -> restoreAnnotationState(annotation, revision.getBeforeState());
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreAnnotationState(Annotation annotation, Map<String, Object> state) {
        if (state == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot roll back this revision");
        }

        Object data = state.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot roll back this revision");
        }

        annotation.setDeleted(false);
        annotation.setDeletedAt(null);
        annotation.setData(new HashMap<>((Map<String, Object>) dataMap));
    }

    private void recordRevision(
            Annotation annotation,
            RevisionAction action,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            User changedBy
    ) {
        annotationRevisionRepository.save(new AnnotationRevision(
                annotation,
                action,
                beforeState,
                afterState,
                changedBy
        ));
    }

    private Map<String, Object> snapshot(Annotation annotation) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("pageNumber", annotation.getPageNumber());
        snapshot.put("type", annotation.getType().name());
        snapshot.put("data", annotation.getData());
        snapshot.put("deleted", annotation.isDeleted());
        return snapshot;
    }

    private DocumentVersion requireDocumentVersion(UUID documentId, int versionNumber) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        return documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document version not found"));
    }

    private Annotation requireActiveAnnotation(UUID annotationId) {
        return annotationRepository.findByIdAndDeletedFalse(annotationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Annotation not found"));
    }

    private void assertCanEditAnnotation(Annotation annotation, Project project, User user, UserRole role) {
        if (role == UserRole.DIRECTOR) {
            return;
        }
        if (role == UserRole.PROJECT_MANAGER && projectAccessService.hasProjectAccess(project, user)) {
            return;
        }
        if (role == UserRole.SITE_WORKER && annotation.getCreatedBy().getId().equals(user.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to edit this annotation");
    }

    private void assertCanRollback(Annotation annotation, Project project, User user, UserRole role) {
        if (role == UserRole.DIRECTOR) {
            return;
        }
        if (role == UserRole.PROJECT_MANAGER && projectAccessService.hasProjectAccess(project, user)) {
            return;
        }
        if (role == UserRole.SITE_WORKER && annotation.getCreatedBy().getId().equals(user.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to roll back this annotation");
    }
}
