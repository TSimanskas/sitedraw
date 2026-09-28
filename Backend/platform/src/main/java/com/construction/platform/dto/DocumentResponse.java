package com.construction.platform.dto;

import com.construction.platform.domain.Document;
import com.construction.platform.domain.DocumentVersion;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID projectId,
        String title,
        String category,
        int versionNumber,
        int versionCount,
        int pageCount,
        long fileSizeBytes,
        UUID uploadedById,
        String uploadedByName,
        Instant uploadedAt
) {

    public static DocumentResponse from(Document document, DocumentVersion version, int versionCount) {
        var uploader = version.getUploadedBy();
        return new DocumentResponse(
                document.getId(),
                document.getProject().getId(),
                document.getTitle(),
                document.getCategory(),
                version.getVersionNumber(),
                versionCount,
                version.getPageCount(),
                version.getFileSizeBytes(),
                uploader != null ? uploader.getId() : null,
                uploader != null ? uploader.getFullName() : null,
                version.getUploadedAt()
        );
    }
}
