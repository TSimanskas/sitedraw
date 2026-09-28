package com.construction.platform.dto;

import com.construction.platform.domain.DocumentVersion;
import com.construction.platform.domain.DocumentVersionSource;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
        UUID id,
        UUID documentId,
        int versionNumber,
        int pageCount,
        long fileSizeBytes,
        UUID uploadedById,
        String uploadedByName,
        Instant uploadedAt,
        DocumentVersionSource source
) {

    public static DocumentVersionResponse from(DocumentVersion version) {
        var uploader = version.getUploadedBy();
        return new DocumentVersionResponse(
                version.getId(),
                version.getDocument().getId(),
                version.getVersionNumber(),
                version.getPageCount(),
                version.getFileSizeBytes(),
                uploader != null ? uploader.getId() : null,
                uploader != null ? uploader.getFullName() : null,
                version.getUploadedAt(),
                version.getSource() != null ? version.getSource() : DocumentVersionSource.ORIGINAL
        );
    }
}
