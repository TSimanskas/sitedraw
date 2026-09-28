package com.construction.platform.dto;

import com.construction.platform.domain.Annotation;
import com.construction.platform.domain.AnnotationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnnotationResponse(
        UUID id,
        UUID documentVersionId,
        UUID documentId,
        int pageNumber,
        AnnotationType type,
        Map<String, Object> data,
        UUID createdById,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted
) {

    public static AnnotationResponse from(Annotation annotation) {
        var creator = annotation.getCreatedBy();
        return new AnnotationResponse(
                annotation.getId(),
                annotation.getDocumentVersion().getId(),
                annotation.getDocumentVersion().getDocument().getId(),
                annotation.getPageNumber(),
                annotation.getType(),
                annotation.getData(),
                creator.getId(),
                creator.getFullName(),
                annotation.getCreatedAt(),
                annotation.getUpdatedAt(),
                annotation.isDeleted()
        );
    }
}
