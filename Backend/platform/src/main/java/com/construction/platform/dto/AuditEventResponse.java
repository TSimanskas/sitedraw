package com.construction.platform.dto;

import com.construction.platform.domain.AuditAction;
import com.construction.platform.domain.AuditEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID userId,
        String userName,
        AuditAction action,
        String entityType,
        UUID entityId,
        UUID projectId,
        UUID documentVersionId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Instant createdAt
) {

    public static AuditEventResponse from(AuditEvent event) {
        var user = event.getUser();
        return new AuditEventResponse(
                event.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : null,
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getProject() != null ? event.getProject().getId() : null,
                event.getDocumentVersionId(),
                event.getBeforeState(),
                event.getAfterState(),
                event.getCreatedAt()
        );
    }
}
