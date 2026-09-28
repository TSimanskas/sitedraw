package com.construction.platform.repository;

import com.construction.platform.domain.Project;
import com.construction.platform.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByOrderByUpdatedAtDesc();

    @Query("""
            SELECT DISTINCT p FROM Project p
            LEFT JOIN FETCH p.createdBy
            INNER JOIN ProjectMember pm ON pm.project = p
            WHERE pm.user = :user
            ORDER BY p.updatedAt DESC
            """)
    List<Project> findAccessibleByUser(@Param("user") User user);
}
