package com.construction.platform.repository;

import com.construction.platform.domain.Annotation;
import com.construction.platform.domain.AnnotationRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnotationRevisionRepository extends JpaRepository<AnnotationRevision, UUID> {

    List<AnnotationRevision> findByAnnotationIdOrderByChangedAtDesc(UUID annotationId);

    Optional<AnnotationRevision> findFirstByAnnotationIdOrderByChangedAtDesc(UUID annotationId);
}
