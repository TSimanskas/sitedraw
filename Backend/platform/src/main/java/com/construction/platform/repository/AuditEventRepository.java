package com.construction.platform.repository;

import com.construction.platform.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            SELECT e FROM AuditEvent e
            WHERE e.project.id = :projectId
            ORDER BY e.createdAt DESC
            """)
    List<AuditEvent> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

    @Query("""
            SELECT e FROM AuditEvent e
            WHERE e.documentVersionId = :documentVersionId
            ORDER BY e.createdAt DESC
            """)
    List<AuditEvent> findByDocumentVersionIdOrderByCreatedAtDesc(@Param("documentVersionId") UUID documentVersionId);

    @Query("""
            SELECT e FROM AuditEvent e
            WHERE e.user.id = :userId
            ORDER BY e.createdAt DESC
            """)
    List<AuditEvent> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);
}
