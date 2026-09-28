package com.construction.platform.service;

import com.construction.platform.domain.AuditAction;
import com.construction.platform.domain.Document;
import com.construction.platform.domain.DocumentVersion;
import com.construction.platform.domain.DocumentVersionSource;
import com.construction.platform.domain.Project;
import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.dto.DocumentResponse;
import com.construction.platform.dto.DocumentVersionResponse;
import com.construction.platform.dto.MarkupAnnotationRequest;
import com.construction.platform.dto.SaveMarkupVersionRequest;
import com.construction.platform.exception.ApiException;
import com.construction.platform.repository.DocumentRepository;
import com.construction.platform.repository.DocumentVersionRepository;
import com.construction.platform.security.UserPrincipal;
import com.construction.platform.storage.StorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ProjectAccessService projectAccessService;
    private final StorageService storageService;
    private final AuditService auditService;
    private final AnnotationService annotationService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            ProjectAccessService projectAccessService,
            StorageService storageService,
            AuditService auditService,
            AnnotationService annotationService
    ) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.projectAccessService = projectAccessService;
        this.storageService = storageService;
        this.auditService = auditService;
        this.annotationService = annotationService;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID projectId, String query, UserPrincipal principal) {
        Project project = projectAccessService.requireProject(projectId);
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(project, user, principal.getRole());

        String needle = normalizeQuery(query);

        return documentRepository.findByProjectOrderByUpdatedAtDesc(project).stream()
                .filter(document -> matchesDocument(document, needle))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DocumentResponse uploadDocument(
            UUID projectId,
            MultipartFile file,
            String title,
            String category,
            UserPrincipal principal,
            String ipAddress
    ) {
        Project project = projectAccessService.requireProject(projectId);
        User uploader = projectAccessService.requireUser(principal);
        assertCanUpload(project, uploader, principal.getRole());

        PreparedPdf pdf = preparePdf(file);
        String resolvedTitle = resolveTitle(title, file.getOriginalFilename());

        Document document = documentRepository.save(new Document(
                project,
                resolvedTitle,
                trimToNull(category),
                uploader
        ));

        DocumentVersion version = storeVersion(document, 1, pdf, uploader, null, DocumentVersionSource.ORIGINAL);

        auditService.log(
                uploader,
                AuditAction.DOCUMENT_UPLOADED,
                "DOCUMENT",
                document.getId(),
                project,
                version.getId(),
                null,
                Map.of("title", document.getTitle(), "versionNumber", 1),
                ipAddress
        );

        return DocumentResponse.from(document, version, 1);
    }

    @Transactional
    public DocumentResponse uploadNewVersion(
            UUID documentId,
            MultipartFile file,
            UserPrincipal principal,
            String ipAddress
    ) {
        Document document = requireDocument(documentId);
        User uploader = projectAccessService.requireUser(principal);
        assertCanUpload(document.getProject(), uploader, principal.getRole());

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        if (versions.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document version not found");
        }

        DocumentVersion previous = versions.get(0);
        PreparedPdf pdf = preparePdf(file);
        DocumentVersion version = storeVersion(
                document,
                previous.getVersionNumber() + 1,
                pdf,
                uploader,
                previous,
                DocumentVersionSource.UPLOAD
        );

        document.touch();
        documentRepository.save(document);

        auditService.log(
                uploader,
                AuditAction.DOCUMENT_UPLOADED,
                "DOCUMENT",
                document.getId(),
                document.getProject(),
                version.getId(),
                Map.of("versionNumber", previous.getVersionNumber()),
                Map.of("versionNumber", version.getVersionNumber()),
                ipAddress
        );

        return DocumentResponse.from(document, version, versions.size() + 1);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> listVersions(UUID documentId, UserPrincipal principal) {
        Document document = requireDocument(documentId);
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(document.getProject(), user, principal.getRole());

        return documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document).stream()
                .map(DocumentVersionResponse::from)
                .toList();
    }

    @Transactional
    public DocumentVersionResponse saveMarkupVersion(
            UUID documentId,
            int sourceVersionNumber,
            SaveMarkupVersionRequest request,
            UserPrincipal principal,
            String ipAddress
    ) {
        Document document = requireDocument(documentId);
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(document.getProject(), user, principal.getRole());

        DocumentVersion source = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, sourceVersionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document version not found"));

        List<MarkupAnnotationRequest> annotations = request.annotations() == null ? List.of() : request.annotations();
        for (MarkupAnnotationRequest annotation : annotations) {
            if (annotation.pageNumber() < 1 || annotation.pageNumber() > source.getPageCount()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid page number");
            }
        }

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        if (versions.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document version not found");
        }

        int nextVersionNumber = versions.get(0).getVersionNumber() + 1;
        DocumentVersion created = copyVersion(
                document,
                source,
                nextVersionNumber,
                user,
                DocumentVersionSource.MARKUP
        );

        document.touch();
        documentRepository.save(document);

        for (MarkupAnnotationRequest annotation : annotations) {
            annotationService.createMarkupAnnotation(
                    documentId,
                    created.getVersionNumber(),
                    source,
                    annotation,
                    principal,
                    ipAddress
            );
        }

        auditService.log(
                user,
                AuditAction.DOCUMENT_VERSION_SAVED,
                "DOCUMENT",
                document.getId(),
                document.getProject(),
                created.getId(),
                Map.of("versionNumber", source.getVersionNumber()),
                Map.of("versionNumber", created.getVersionNumber(), "annotationCount", annotations.size()),
                ipAddress
        );

        return DocumentVersionResponse.from(created);
    }

    @Transactional
    public void deleteDocument(UUID documentId, UserPrincipal principal, String ipAddress) {
        Document document = requireDocument(documentId);
        User actor = projectAccessService.requireUser(principal);
        projectAccessService.assertCanManage(
                document.getProject(),
                actor,
                principal.getRole(),
                ProjectAccessService.CANNOT_UPLOAD_DOCUMENTS
        );

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        for (DocumentVersion version : versions) {
            storageService.delete(version.getStorageKey());
            if (version.getBackupStorageKey() != null) {
                storageService.delete(version.getBackupStorageKey());
            }
        }

        auditService.log(
                actor,
                AuditAction.DOCUMENT_DELETED,
                "DOCUMENT",
                document.getId(),
                document.getProject(),
                versions.isEmpty() ? null : versions.get(0).getId(),
                Map.of("title", document.getTitle(), "versionCount", versions.size()),
                null,
                ipAddress
        );

        documentVersionRepository.deleteAll(versions);
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentFileResource openDocumentFile(UUID documentId, int versionNumber, UserPrincipal principal) {
        Document document = requireDocument(documentId);
        User user = projectAccessService.requireUser(principal);
        projectAccessService.assertCanView(document.getProject(), user, principal.getRole());

        DocumentVersion version = documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document version not found"));

        return new DocumentFileResource(
                sanitizeFilename(document.getTitle()) + ".pdf",
                storageService.download(version.getStorageKey()),
                version.getFileSizeBytes()
        );
    }

    private DocumentResponse toResponse(Document document) {
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        if (versions.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document version not found");
        }
        return DocumentResponse.from(document, versions.get(0), versions.size());
    }

    private DocumentVersion storeVersion(
            Document document,
            int versionNumber,
            PreparedPdf pdf,
            User uploader,
            DocumentVersion previous,
            DocumentVersionSource source
    ) {
        String storageKey = buildStorageKey(document.getId(), versionNumber, "original.pdf");
        String backupStorageKey = buildStorageKey(
                document.getId(),
                versionNumber,
                "backup-" + Instant.now().toEpochMilli() + ".pdf"
        );

        storageService.upload(storageKey, new ByteArrayInputStream(pdf.content()), pdf.content().length, PDF_CONTENT_TYPE);
        storageService.copy(storageKey, backupStorageKey);

        DocumentVersion version = new DocumentVersion(
                document,
                versionNumber,
                storageKey,
                backupStorageKey,
                pdf.pageCount(),
                pdf.content().length,
                uploader
        );
        version.setSource(source);

        if (previous != null) {
            version.setCalibrationPixelsPerUnit(previous.getCalibrationPixelsPerUnit());
            version.setCalibrationUnitLabel(previous.getCalibrationUnitLabel());
        }

        return documentVersionRepository.save(version);
    }

    private DocumentVersion copyVersion(
            Document document,
            DocumentVersion source,
            int versionNumber,
            User uploader,
            DocumentVersionSource sourceType
    ) {
        String storageKey = buildStorageKey(document.getId(), versionNumber, "original.pdf");
        String backupStorageKey = buildStorageKey(
                document.getId(),
                versionNumber,
                "backup-" + Instant.now().toEpochMilli() + ".pdf"
        );

        storageService.copy(source.getStorageKey(), storageKey);
        storageService.copy(storageKey, backupStorageKey);

        DocumentVersion version = new DocumentVersion(
                document,
                versionNumber,
                storageKey,
                backupStorageKey,
                source.getPageCount(),
                source.getFileSizeBytes(),
                uploader
        );
        version.setSource(sourceType);
        version.setCalibrationPixelsPerUnit(source.getCalibrationPixelsPerUnit());
        version.setCalibrationUnitLabel(source.getCalibrationUnitLabel());

        return documentVersionRepository.save(version);
    }

    private PreparedPdf preparePdf(MultipartFile file) {
        byte[] content = readPdfContent(file);
        return new PreparedPdf(content, countPdfPages(content));
    }

    private byte[] readPdfContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF file is required");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }

        String contentType = file.getContentType();
        if (contentType != null && !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file");
        }
    }

    private int countPdfPages(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            return pdf.getNumberOfPages();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid PDF file");
        }
    }

    private String resolveTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            return "Untitled document";
        }
        return originalFilename.replaceAll("(?i)\\.pdf$", "");
    }

    private String buildStorageKey(UUID documentId, int versionNumber, String filename) {
        return "documents/" + documentId + "/v" + versionNumber + "/" + filename;
    }

    private String sanitizeFilename(String title) {
        return title.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Document requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void assertCanUpload(Project project, User user, UserRole role) {
        if (role == UserRole.SITE_WORKER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Site workers cannot upload documents");
        }
        projectAccessService.assertCanManage(project, user, role, ProjectAccessService.CANNOT_UPLOAD_DOCUMENTS);
    }

    private boolean matchesDocument(Document document, String needle) {
        if (needle == null) {
            return true;
        }
        return contains(document.getTitle(), needle) || contains(document.getCategory(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PreparedPdf(byte[] content, int pageCount) {
    }
}
