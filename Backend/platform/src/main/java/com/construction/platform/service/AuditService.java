package com.construction.platform.service;

import com.construction.platform.domain.AuditAction;
import com.construction.platform.domain.AuditEvent;
import com.construction.platform.domain.Project;
import com.construction.platform.domain.User;
import com.construction.platform.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void log(
            User user,
            AuditAction action,
            String entityType,
            UUID entityId,
            Project project,
            UUID documentVersionId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String ipAddress
    ) {
        auditEventRepository.save(new AuditEvent(
                user,
                action,
                entityType,
                entityId,
                project,
                documentVersionId,
                beforeState,
                afterState,
                ipAddress
        ));
    }
}
