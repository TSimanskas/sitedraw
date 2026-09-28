package com.construction.platform.repository;

import com.construction.platform.domain.Document;
import com.construction.platform.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByProjectOrderByUpdatedAtDesc(Project project);
}
