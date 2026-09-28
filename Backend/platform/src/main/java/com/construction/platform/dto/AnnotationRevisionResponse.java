package com.construction.platform.dto;

import com.construction.platform.domain.AnnotationRevision;
import com.construction.platform.domain.RevisionAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnnotationRevisionResponse(
        UUID id,
        UUID annotationId,
        RevisionAction action,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        UUID changedById,
        String changedByName,
        Instant changedAt
) {

    public static AnnotationRevisionResponse from(AnnotationRevision revision) {
        var changedBy = revision.getChangedBy();
        return new AnnotationRevisionResponse(
                revision.getId(),
                revision.getAnnotation().getId(),
                revision.getAction(),
                revision.getBeforeState(),
                revision.getAfterState(),
                changedBy.getId(),
                changedBy.getFullName(),
                revision.getChangedAt()
        );
    }
}
