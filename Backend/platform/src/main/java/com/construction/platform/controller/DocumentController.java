package com.construction.platform.controller;

import com.construction.platform.dto.DocumentResponse;
import com.construction.platform.dto.DocumentVersionResponse;
import com.construction.platform.dto.SaveMarkupVersionRequest;
import com.construction.platform.security.UserPrincipal;
import com.construction.platform.service.DocumentFileResource;
import com.construction.platform.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/projects/{projectId}/documents")
    public List<DocumentResponse> listDocuments(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return documentService.listDocuments(projectId, q, principal);
    }

    @PostMapping(value = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return documentService.uploadDocument(
                projectId,
                file,
                title,
                category,
                principal,
                httpRequest.getRemoteAddr()
        );
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadNewVersion(
            @PathVariable UUID documentId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return documentService.uploadNewVersion(documentId, file, principal, httpRequest.getRemoteAddr());
    }

    @GetMapping("/documents/{documentId}/versions")
    public List<DocumentVersionResponse> listVersions(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return documentService.listVersions(documentId, principal);
    }

    @PostMapping("/documents/{documentId}/versions/{versionNumber}/markup-versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentVersionResponse saveMarkupVersion(
            @PathVariable UUID documentId,
            @PathVariable int versionNumber,
            @Valid @RequestBody SaveMarkupVersionRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return documentService.saveMarkupVersion(
                documentId,
                versionNumber,
                request,
                principal,
                httpRequest.getRemoteAddr()
        );
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        documentService.deleteDocument(documentId, principal, httpRequest.getRemoteAddr());
    }

    @GetMapping("/documents/{documentId}/versions/{versionNumber}/file")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID documentId,
            @PathVariable int versionNumber,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        DocumentFileResource file = documentService.openDocumentFile(documentId, versionNumber, principal);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .body(new InputStreamResource(file.inputStream()));
    }
}
