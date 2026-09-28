package com.construction.platform.controller;

import com.construction.platform.dto.*;
import com.construction.platform.security.UserPrincipal;
import com.construction.platform.service.AnnotationService;
import com.construction.platform.service.AuditQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AnnotationController {

    private final AnnotationService annotationService;
    private final AuditQueryService auditQueryService;

    public AnnotationController(AnnotationService annotationService, AuditQueryService auditQueryService) {
        this.annotationService = annotationService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/documents/{documentId}/versions/{versionNumber}/annotations")
    public List<AnnotationResponse> listAnnotations(
            @PathVariable UUID documentId,
            @PathVariable int versionNumber,
            @RequestParam(required = false) Integer page,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return annotationService.listAnnotations(documentId, versionNumber, page, principal);
    }

    @PostMapping("/documents/{documentId}/versions/{versionNumber}/annotations")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnotationResponse createAnnotation(
            @PathVariable UUID documentId,
            @PathVariable int versionNumber,
            @Valid @RequestBody CreateAnnotationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return annotationService.createAnnotation(
                documentId,
                versionNumber,
                request,
                principal,
                httpRequest.getRemoteAddr()
        );
    }

    @PutMapping("/annotations/{annotationId}")
    public AnnotationResponse updateAnnotation(
            @PathVariable UUID annotationId,
            @Valid @RequestBody UpdateAnnotationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return annotationService.updateAnnotation(
                annotationId,
                request,
                principal,
                httpRequest.getRemoteAddr()
        );
    }

    @DeleteMapping("/annotations/{annotationId}")
    public void deleteAnnotation(
            @PathVariable UUID annotationId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        annotationService.deleteAnnotation(annotationId, principal, httpRequest.getRemoteAddr());
    }

    @GetMapping("/annotations/{annotationId}/revisions")
    public List<AnnotationRevisionResponse> listRevisions(
            @PathVariable UUID annotationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return annotationService.listRevisions(annotationId, principal);
    }

    @PostMapping("/annotations/{annotationId}/rollback")
    public AnnotationResponse rollbackAnnotation(
            @PathVariable UUID annotationId,
            @RequestBody(required = false) RollbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return annotationService.rollbackAnnotation(
                annotationId,
                request,
                principal,
                httpRequest.getRemoteAddr()
        );
    }

    @GetMapping("/documents/{documentId}/versions/{versionNumber}/calibration")
    public CalibrationResponse getCalibration(
            @PathVariable UUID documentId,
            @PathVariable int versionNumber,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return annotationService.getCalibration(documentId, versionNumber, principal);
    }

    @PutMapping("/documents/{documentId}/versions/{versionNumber}/calibration")
    public CalibrationResponse updateCalibration(
            @PathVariable UUID documentId,
            @PathVariable int versionNumber,
            @Valid @RequestBody CalibrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return annotationService.updateCalibration(documentId, versionNumber, request, principal);
    }

    @GetMapping("/audit-events")
    public List<AuditEventResponse> listAuditEvents(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID documentVersionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return auditQueryService.listAuditEvents(projectId, documentVersionId, principal);
    }
}
