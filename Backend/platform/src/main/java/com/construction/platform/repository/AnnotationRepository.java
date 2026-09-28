package com.construction.platform.repository;

import com.construction.platform.domain.Annotation;
import com.construction.platform.domain.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnotationRepository extends JpaRepository<Annotation, UUID> {

    List<Annotation> findByDocumentVersionAndPageNumberAndDeletedFalseOrderByCreatedAtAsc(
            DocumentVersion documentVersion,
            int pageNumber
    );

    List<Annotation> findByDocumentVersionAndDeletedFalseOrderByCreatedAtAsc(DocumentVersion documentVersion);

    Optional<Annotation> findByIdAndDeletedFalse(UUID id);
}
